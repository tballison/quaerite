/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tallison.quaerite.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.log4j.Logger;
import org.tallison.quaerite.connectors.QueryRequest;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.Experiment;
import org.tallison.quaerite.core.ExperimentConfig;
import org.tallison.quaerite.core.ExperimentSet;
import org.tallison.quaerite.core.JudgmentList;
import org.tallison.quaerite.core.Judgments;
import org.tallison.quaerite.core.QueryInfo;
import org.tallison.quaerite.core.QueryStrings;
import org.tallison.quaerite.core.SearchResultSet;
import org.tallison.quaerite.core.features.CustomHandler;
import org.tallison.quaerite.core.queries.Query;
import org.tallison.quaerite.core.queries.TermsQuery;
import org.tallison.quaerite.core.scorers.AbstractJudgmentScorer;
import org.tallison.quaerite.core.scorers.DistributionalScoreAggregator;
import org.tallison.quaerite.core.scorers.JudgmentScorer;
import org.tallison.quaerite.core.scorers.Scorer;
import org.tallison.quaerite.core.scorers.SearchResultSetScorer;
import org.tallison.quaerite.core.scorers.SummingScoreAggregator;
import org.tallison.quaerite.core.util.MapUtil;
import org.tallison.quaerite.db.ExperimentDB;
import org.tallison.quaerite.db.QueryRunnerDBClient;

public abstract class AbstractExperimentRunner extends AbstractCLI {
    static final Judgments POISON = new Judgments(new QueryInfo("",
            "", new QueryStrings(), -1));

    static Logger LOG = Logger.getLogger(AbstractExperimentRunner.class);

    //number of retries allowed for querying the search application
    static final int MAX_RETRIES = 2;

    static final int DEFAULT_NUM_THREADS = 8;
    private static final int MAX_MATRIX_COLS = 100;
    //this caches a judgment list of valid judgments
    //per search server url
    Map<String, JudgmentList> searchServerValidatedMap = new HashMap<>();

    private final ExperimentConfig experimentConfig;
    NumberFormat threePlaces = new DecimalFormat(".000",
            DecimalFormatSymbols.getInstance(Locale.US));

    public AbstractExperimentRunner(ExperimentConfig experimentConfig) {
        this.experimentConfig = experimentConfig;
    }


    void runExperiment(Experiment experiment, List<Scorer> scorers,
                       int maxRows, ExperimentDB experimentDB, JudgmentList judgmentList,
                       String judgmentListId, boolean logResults)
            throws SQLException, IOException, SearchClientException {
        if (experimentDB.hasScores(experiment.getName())) {
            LOG.info("Already has scores for " + experiment.getName() + "; skipping.  " +
                    "Use the -freshStart commandline option to clear all scores");
            return;
        }
        experimentDB.initScoreTable(scorers);
        //this client is only used in a single thread!
        SearchClient searchClient =
                SearchClientFactory.getClient(experiment.getServerConnection());

        if (StringUtils.isBlank(experimentConfig.getIdField())) {
            LOG.info("default document 'idField' not set in experiment config. " +
                    "Will use default: '"
                    + searchClient.getDefaultIdField() + "'");
            experimentConfig.setIdField(searchClient.getDefaultIdField());
        }

        JudgmentList validated = searchServerValidatedMap.get(
                experiment.getServerConnection() +
                        "_" + judgmentListId);
        if (validated == null) {

            validated = validate(searchClient, experiment.getCustomHandler(),
                    judgmentList,
                    experimentConfig.getSleep());
            searchServerValidatedMap.put(experiment.getServerConnection()
                    + "_" + judgmentListId, validated);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(
                experimentConfig.getNumThreads());
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(executorService);
        ArrayBlockingQueue<Judgments> queue = new ArrayBlockingQueue<>(
                validated.getJudgmentsList().size() +
                        experimentConfig.getNumThreads());

        queue.addAll(validated.getJudgmentsList());
        for (int i = 0; i < experimentConfig.getNumThreads(); i++) {
            queue.add(POISON);
        }

        for (int i = 0; i < experimentConfig.getNumThreads(); i++) {
            executorCompletionService.submit(
                    new QueryRunner(experimentConfig.getIdField(),
                            experimentConfig.getSleep(),
                            maxRows,
                            queue, experiment, experimentDB, scorers));
        }

        int completed = 0;
        while (completed < experimentConfig.getNumThreads()) {
            try {
                Future<Integer> future = executorCompletionService.take();
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                completed++;
            }
        }
        executorService.shutdown();
        executorService.shutdownNow();
        //insertScores(experimentDB, experimentName, scoreAggregators);
        experimentDB.insertScoresAggregated(experiment.getName(), scorers);
        if (logResults) {
            logResults(experiment.getName(), scorers);
        }
    }

    private void logResults(String experimentName, List<Scorer> scorers) {
        StringBuilder result = new StringBuilder();
        LOG.info("Experiment: " + experimentName);
        for (Scorer scorer : scorers) {
            for (String querySetName : scorer.getQuerySets()) {
                Map<String, Double> summaryStats =
                        scorer.getSummaryStatistics(querySetName);
                if (!StringUtils.isBlank(querySetName)) {
                    result.append("Query Set: ").append(querySetName);
                } else {
                    result.append("All Queries: ");
                }
                result.append(scorer.getName());
                result.append(" - ");
                if (scorer instanceof SummingScoreAggregator) {
                    result.append("sum: ");
                    result.append(getValueString(summaryStats.get(SummingScoreAggregator.SUM)));
                } else if (scorer instanceof DistributionalScoreAggregator) {
                    result.append("mean: ");
                    result.append(
                            getValueString(summaryStats.get(DistributionalScoreAggregator.MEAN)));
                    result.append(", median: ");
                    result.append(
                            getValueString(summaryStats.get(DistributionalScoreAggregator.MEDIAN)));
                }
                LOG.info(result);
                result.setLength(0);
            }
        }
    }

    protected String getValueString(Double value) {

        if (value != null) {
            if ((long) value.doubleValue() == value) {
                return Long.toString((long) value.doubleValue());
            } else {
                return threePlaces.format(value);
            }
        } else {
            return "couldn't find value?!";
        }
    }

    /*
    private void insertScores(ExperimentDB experimentDB, String experimentName,
     List<ScoreAggregator> scoreAggregators)
            throws SQLException {
        Set<QueryInfo> queries = scoreAggregators.get(0).getScores().keySet();
        //TODO -- need to add better handling for missing queries
        Map<String, Double> tmpScores = new HashMap<>();
        for (QueryInfo queryInfo : queries) {
            tmpScores.clear();
            for (ScoreAggregator scoreAggregator : scoreAggregators) {
                double val = scoreAggregator.getScores().get(queryInfo);
                tmpScores.put(scoreAggregator.getName(), val);
            }
            experimentDB.insertScores(queryInfo, experimentName, scoreAggregators,
            tmpScores);
        }
    }

     */

    //TODO -- make this multi threaded

    /**
     * This reads through the judgment list and makes sure that the
     * a document with a given judgment's id is actually available in the
     * index.  This removes those ids that are not in the index and returns
     * a winnowed/validated {@link JudgmentList}.
     *
     * @param searchClient
     * @param judgmentList
     * @return
     */
    private JudgmentList validate(SearchClient searchClient, CustomHandler customHandler,
                                  JudgmentList judgmentList, long sleep)
            throws IOException, SearchClientException {
        String idField = searchClient.getIdField(experimentConfig);
        Set<String> judgmentIds = new HashSet<>();
        for (Judgments j : judgmentList.getJudgmentsList()) {
            judgmentIds.addAll(j.getSortedJudgments().keySet());
        }
        LOG.info("about to validate " + judgmentIds.size() + " judgment ids");
        Set<String> valid = new HashSet<>();

        int len = 0;
        List<String> ids = new ArrayList<>();
        for (String id : judgmentIds) {
            ids.add(id);
            len += id.length();
            if (len > 1000) {
                addValid(new TermsQuery(idField, ids), customHandler,
                        idField, searchClient, ids.size(), valid);
                len = 0;
                ids.clear();
                if (experimentConfig.getSleep() > 0) {
                    try {
                        Thread.sleep(experimentConfig.getSleep());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (ids.size() > 0) {
            addValid(new TermsQuery(idField, ids), customHandler,
                    idField, searchClient, ids.size(), valid);
        }

        int validIds = 0;
        int invalidIds = 0;
        if (judgmentIds.size() != valid.size()) {
            for (String id : judgmentIds) {
                if (!valid.contains(id)) {
                    invalidIds++;
                    LOG.info("I regret that I could not find: id:" + id + " in the index. " +
                            "I'll remove this from the judgments before scoring.");
                } else {
                    validIds++;
                }
            }
        }
        if (invalidIds > 0) {
            LOG.warn("There were " + validIds + " unique valid ids and " +
                    invalidIds + " unique invalid ids");
        }
        int validQueries = 0;
        int invalidQueries = 0;
        JudgmentList retList = new JudgmentList();
        for (Judgments j : judgmentList.getJudgmentsList()) {
            //defensively copy
            Judgments winnowedJugments = new Judgments(
                    new QueryInfo(j.getQueryInfo().getQueryId(),
                            j.getQuerySet(), j.getQueryStrings(), j.getQueryCount()));
            for (Map.Entry<String, Double> e : j.getSortedJudgments().entrySet()) {
                if (valid.contains(e.getKey())) {
                    winnowedJugments.addJudgment(e.getKey(), e.getValue());
                } else {
                    LOG.debug("Could not find " + e.getKey() + " in the index!");
                }
            }
            if (winnowedJugments.getSortedJudgments().size() > 0) {
                retList.addJudgments(winnowedJugments);
                validQueries++;
            } else {
                LOG.warn(
                        "After removing invalid jugments, there were 0 " +
                                "judgments for query: " +
                                j.getQueryInfo().getQueryId());
                invalidQueries++;
            }
        }
        if (invalidQueries > 0) {
            LOG.warn("I had to remove " + invalidQueries +
                    " queries because there were no judgments for them. " +
                    " There were " + validQueries + " valid queries.");
        }
        LOG.info("finished validating " + judgmentIds.size() + " judgment ids");
        return retList;

    }

    private static void addValid(TermsQuery termsQuery, CustomHandler customHandler, String idField,
                                 SearchClient searchClient, int expected,
                                 Set<String> valid) {
        if (expected == 0) {
            return;
        }
        QueryRequest q = new QueryRequest(termsQuery, customHandler, idField);
        q.addFieldsToRetrieve(idField);
        q.setNumResults(expected * 2);
        SearchResultSet searchResultSet;
        try {
            searchResultSet = searchClient.search(q);
        } catch (SearchClientException | IOException e) {
            throw new RuntimeException(e);
        }
        Set<String> localValid = new HashSet<>();
        Set<String> terms = new HashSet<>(termsQuery.getTerms());
        for (int i = 0; i < searchResultSet.size(); i++) {
            String id = searchResultSet.getId(i);
            if (localValid.contains(id)) {
                LOG.warn("Found non-unique key: " + id);
            }
            if (!terms.contains(id)) {
                LOG.error("Search returned an id I wasn't looking for: "
                        + id +
                        ". This is fatal and can mean that there's a default queryparser that" +
                        " is not correctly parsing a terms query");
            }
            valid.add(id);
            localValid.add(id);
        }

    }


    static class QueryRunner implements Callable<Integer> {
        private static AtomicInteger IDs = new AtomicInteger();
        private static AtomicInteger PROCESSED = new AtomicInteger();
        private final int threadNum = IDs.getAndIncrement();

        private final String idField;
        private final int maxRows;
        private final long sleep;
        private final ArrayBlockingQueue<Judgments> queue;
        private final Experiment experiment;
        private final Query query;//thread safe clone of the query
        private final List<Scorer> scorers;
        private final SearchClient searchClient;//created fresh one per thread
        private final QueryRunnerDBClient dbClient;
        private int batched = 0;

        public QueryRunner(String idField, long sleep, int maxRows, ArrayBlockingQueue<Judgments> judgments,
                           Experiment experiment, ExperimentDB experimentDB,
                           List<Scorer> scorers) throws SQLException, IOException, SearchClientException {
            this.idField = idField;
            this.sleep = sleep;
            this.maxRows = maxRows;
            this.queue = judgments;
            this.experiment = experiment;
            this.query = experiment.getQuery();
            this.searchClient =
                    SearchClientFactory.getClient(experiment.getServerConnection());
            this.scorers = scorers;
            this.dbClient = experimentDB.getQueryRunnerDBClient(scorers);
        }

        @Override
        public Integer call() throws Exception {

            try {
                while (true) {
                    Judgments judgments = queue.poll();
                    if (judgments.equals(POISON)) {
//                    LOG.trace(threadNum + ": scorer thread hit poison. stopping now");
                        return 1;
                    }
                    scoreEach(judgments, scorers);
                    if (batched++ > 100) {
                        batched = 0;
                        dbClient.executeBatch();
                    }
                    if (sleep > 0) {
                        Thread.sleep(sleep);
                    }
                }
            } finally {
                Exception ex = null;
                try {
                    dbClient.close();
                } catch (Exception e) {
                    ex = e;
                }
                searchClient.close();
                if (ex != null) {
                    throw ex;
                }
            }
        }

        private void scoreEach(Judgments judgments,
                               List<Scorer> scorers) throws SQLException {
            query.setQueryStrings(judgments.getQueryStrings());

            QueryRequest queryRequest = new QueryRequest(query, experiment.getCustomHandler(), idField);
            queryRequest.addFieldsToRetrieve(idField);
            if (experiment.getFilterQueries().size() > 0) {
                queryRequest.addFilterQueries(experiment.getFilterQueries());
            }
            queryRequest.setNumResults(maxRows);

            SearchResultSet searchResultSet = null;
            int tries = 0;
            boolean success = false;
            while (! success && tries++ < MAX_RETRIES) {
                try {
                    searchResultSet = searchClient.search(queryRequest);
                    success = true;
                } catch (SearchClientException | IOException e) {
                    //TODO add exception to searchResultSet and log
                    LOG.warn("error getting results for: "
                            + judgments.getQueryStrings(), e);
                }
            }
            if (success == false || searchResultSet == null) {
                LOG.warn("failed to get results for: " +
                        judgments.getQueryStrings() + ". Ignoring this query.");
                return;
            }
            dbClient.insertSearchResults(judgments.getQueryInfo(),
                    experiment.getName(), searchResultSet);

            for (Scorer scorer : scorers) {
                if (scorer instanceof JudgmentScorer) {
                    ((JudgmentScorer) scorer).score(judgments, searchResultSet);
                } else if (scorer instanceof SearchResultSetScorer) {
                    ((SearchResultSetScorer) scorer).score(judgments.getQueryInfo(),
                            searchResultSet);
                } else {
                    throw new IllegalArgumentException("Scorer class not yet supported: "
                            + scorer.getClass());
                }
            }
            LOG.debug("processed '" + judgments.getQueryStrings()
                    + "'; total: " + PROCESSED.incrementAndGet());
            dbClient.insertScores(judgments.getQueryInfo(), experiment.getName(), scorers);
        }
    }


    ////////////DUMP RESULTS
    static void dumpResults(ExperimentSet experimentSet, ExperimentDB experimentDB,
                            List<String> querySets,
                            List<Scorer> scorers, Path outputDir, boolean isTest) throws Exception {
        if (!Files.isDirectory(outputDir)) {
            Files.createDirectories(outputDir);
        }
        dumpPerQuery(experimentDB, outputDir);

        String orderByPriority1 = null;
        String orderByPriority2 = null;
        for (Scorer scorer : experimentSet.getScorers()) {
            if (isTest && scorer instanceof AbstractJudgmentScorer &&
                    ((AbstractJudgmentScorer) scorer).getUseForTest()) {
                orderByPriority1 = scorer.getPrimaryStatisticName();
                break;
            }
            if (scorer instanceof AbstractJudgmentScorer &&
                    ((AbstractJudgmentScorer) scorer).getUseForTrain()) {
                orderByPriority2 = scorer.getPrimaryStatisticName();
            }
        }
        String orderBy = "";
        if (orderByPriority1 != null) {
            orderBy = " order by " + orderByPriority1 + " desc";
        } else if (orderByPriority1 == null && orderByPriority2 != null) {
            orderBy = " order by " + orderByPriority2 + " desc";
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDir.resolve("scores_aggregated.csv"), StandardCharsets.UTF_8)) {
            try (Statement st = experimentDB.getConnection().createStatement()) {
                try (java.sql.ResultSet resultSet =
                             st.executeQuery("select * from SCORES_AGGREGATED "
                                     + orderBy)) {
                    writeHeaders(resultSet.getMetaData(), writer);
                    while (resultSet.next()) {
                        writeRow(resultSet, writer);
                    }
                }
                writer.flush();
            }
        }
        if (querySets.size() > 0) {
            for (String querySet : querySets) {
                dumpSignificanceMatrices(querySet, scorers, experimentDB, outputDir);
            }
        }
        //now dump across all query sets
        dumpSignificanceMatrices("", scorers, experimentDB, outputDir);


    }

    private static void dumpPerQuery(ExperimentDB experimentDB, Path outputDir) throws Exception {
        StringBuilder select = new StringBuilder();
        select.append("select " +
                "s.query_id QUERY_ID, " +
                "QUERY_NAME, " +
                "s.query_set QUERY_SET, " +
                "s.query_count QUERY_COUNT, " +
                "EXPERIMENT");
        for (String scorer : experimentDB.getScoreAggregatorNames()) {
            select.append(", ").append(scorer);
        }
        select.append(" from SCORES s");
        select.append(" join judgments j on s.query_id=j.query_id");
        if (experimentDB.hasNamedQuerySets()) {
            select.append(" where s.QUERY_SET <> ''");
        }
        select.append(" order by experiment, s.query_set, query_name");
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputDir.resolve("per_query_scores.csv"), StandardCharsets.UTF_8)) {
            try (Statement st = experimentDB.getConnection().createStatement()) {

                try (java.sql.ResultSet resultSet = st.executeQuery(select.toString())) {
                    writeHeaders(resultSet.getMetaData(), writer);
                    while (resultSet.next()) {
                        writeRow(resultSet, writer);
                    }
                }
                writer.flush();
            }
        }
    }

    private static void dumpSignificanceMatrices(String querySet,
                                                 List<Scorer> targetScorers,
                                                 ExperimentDB experimentDB,
                                                 Path outputDir) throws Exception {
        TTest tTest = new TTest();
        for (Scorer scorer : targetScorers) {
            if (scorer instanceof AbstractJudgmentScorer &&
                    ((AbstractJudgmentScorer) scorer).getExportPMatrix()) {
                Map<String, Double> aggregatedScores =
                        experimentDB.getKeyExperimentScore(scorer, querySet);

                Map<String, Double> sorted = MapUtil.sortByDescendingValue(aggregatedScores);
                List<String> experiments = new ArrayList();
                experiments.addAll(sorted.keySet());
                writeMatrix(tTest, (AbstractJudgmentScorer) scorer,
                        querySet, experiments, experimentDB, outputDir);
            }
        }
    }

    private static void writeMatrix(TTest tTest, AbstractJudgmentScorer scorer,
                                    String querySet,
                                    List<String> experiments,
                                    ExperimentDB experimentDB,
                                    Path outputDir) throws Exception {

        String fileName = "sig_diffs_" + scorer.getName() + (
                (StringUtils.isBlank(querySet)) ? ".csv" : "_" + querySet + ".csv");

        List<String> matrixExperiments = new ArrayList<>();
        for (int i = 0; i < experiments.size() && i < MAX_MATRIX_COLS; i++) {
            matrixExperiments.add(experiments.get(i));
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputDir.resolve(fileName))) {

            for (String experiment : matrixExperiments) {
                writer.write(",");
                writer.write(experiment);
            }
            writer.write("\n");

            for (int i = 0; i < matrixExperiments.size(); i++) {
                String experimentA = matrixExperiments.get(i);
                writer.write(experimentA);
                for (int k = 0; k <= i; k++) {
                    writer.write(",");
                }
                writer.write(String.format(Locale.US, "%.3G", 1.0d) + ",");//p-value of itself
                //map of query -> score for experiment A given this particular scorer
                Map<String, Double> scoresA = experimentDB.getScores(querySet,
                        experimentA, scorer.getName());
                for (int j = i + 1; j < matrixExperiments.size(); j++) {
                    String experimentB = matrixExperiments.get(j);
                    double significance =
                            calcSignificance(tTest, querySet, scoresA,
                                    experimentA, experimentB,
                            scorer.getName(), experimentDB);
                    writer.write(String.format(Locale.US, "%.3G", significance));
                    writer.write(",");
                }
                writer.write("\n");
            }
        }
    }

    private static double calcSignificance(TTest tTest, String querySet,
                                           Map<String, Double> scoresA, String experimentA,
                                           String experimentB, String scorer,
                                           ExperimentDB experimentDB) throws SQLException {

        Map<String, Double> scoresB = experimentDB.getScores(querySet, experimentB, scorer);
        if (scoresA.size() != scoresB.size()) {
            //log
            LOG.warn("Different number of scores for " +
                    experimentA + "(" + scoresA.size() +
                    ") vs. " + experimentB + "(" + scoresB.size() + ")");
        }
        double[] arrA = new double[scoresA.size()];
        double[] arrB = new double[scoresB.size()];

        int i = 0;
        for (String query : scoresA.keySet()) {
            Double scoreA = scoresA.get(query);
            Double scoreB = scoresB.get(query);
            if (scoreA == null || scoreA < 0) {
                scoreA = 0.0d;
            }
            if (scoreB == null || scoreB < 0) {
                scoreB = 0.0d;
            }
            arrA[i] = scoreA;
            arrB[i] = scoreB;
            i++;
        }
//        WilcoxonSignedRankTest w = new WilcoxonSignedRankTest();
        //      w.wilcoxonSignedRankTest()
        if (arrA.length < 2) {
            LOG.warn("too few examples for t-test; returning -1");
            return -1;
        }
        return tTest.tTest(arrA, arrB);

    }

    private static void writeHeaders(ResultSetMetaData metaData, BufferedWriter writer)
            throws Exception {
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            writer.write(clean(metaData.getColumnName(i)));
            writer.write(",");
        }
        writer.write("\n");
    }

    private static void writeRow(java.sql.ResultSet resultSet, BufferedWriter writer)
            throws Exception {
        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
            writer.write(clean(resultSet.getString(i)));
            writer.write(",");
        }
        writer.write("\n");
    }

    private static String clean(String string) {
        if (string == null) {
            return "";
        }
        string = string.replaceAll("[\r\n]", " ");
        if (string.contains(",")) {
            string.replaceAll("\"", "\"\"");
            string = "\"" + string + "\"";
        }
        return string;
    }
}
