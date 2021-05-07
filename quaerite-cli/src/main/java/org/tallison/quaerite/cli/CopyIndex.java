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

import static org.tallison.quaerite.core.util.CommandLineUtil.getInt;
import static org.tallison.quaerite.core.util.CommandLineUtil.getString;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.StoredDocument;
import org.tallison.quaerite.core.queries.LuceneQuery;
import org.tallison.quaerite.core.queries.Query;

public class CopyIndex extends AbstractCLI {

    static Logger LOG = LogManager.getLogger(CopyIndex.class);

    private static final int NUM_THREADS = 10;
    private static final int BATCH_SIZE = 100;

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("src")
                        .hasArg().required().desc("source index url").build()
        );

        OPTIONS.addOption(
                Option.builder("dest")
                        .hasArg()
                        .required()
                        .desc("destination index url").build()
        );
        OPTIONS.addOption(
                Option.builder("includeFields")
                        .hasArg()
                        .required(false)
                        .desc("copy only these fields; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("fq")
                        .longOpt("filterQueries")
                        .hasArg()
                        .required(false)
                        .desc("filter queries; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("excludeFields")
                        .hasArg()
                        .required(false)
                        .desc("do not copy these fields; comma-delimited list").build()
        );
        OPTIONS.addOption(
                Option.builder("clean")
                        .hasArg(false)
                        .required(false)
                        .desc("clean (DELETE) the destination index before copying").build()
        );
        OPTIONS.addOption(
                Option.builder("b")
                        .longOpt("batchSize")
                        .hasArg(true)
                        .required(false)
                        .desc("batch size; default: " + BATCH_SIZE).build()
        );
        OPTIONS.addOption(
                Option.builder("n")
                        .longOpt("numThreads")
                        .hasArg(true)
                        .required(false)
                        .desc("num copier threads; default: " + NUM_THREADS).build()
        );
    }

    private int numThreads = NUM_THREADS;
    private int batchSize = BATCH_SIZE;

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.tallison.quaerite.cli.DumpResults",
                    OPTIONS);
            return;
        }
        SearchClient srcClient = SearchClientFactory.getClient(commandLine.getOptionValue("src"));
        SearchClient destClient = SearchClientFactory.getClient(commandLine.getOptionValue("dest"));
        Set<String> includeFields = splitComma(
                getString(commandLine, "includeFields", StringUtils.EMPTY));
        Set<String> excludeFields = splitComma(
                getString(commandLine, "excludeFields", StringUtils.EMPTY));
        Set<String> filterQueryStrings = splitComma(
                getString(commandLine, "fq", StringUtils.EMPTY));
        Set<Query> filterQueries = new HashSet<>();
        for (String q : filterQueryStrings) {
            filterQueries.add(new LuceneQuery("", q));
        }

        excludeFields = updateExcludeList(srcClient, excludeFields);

        LOG.debug("includeFields:" + includeFields);
        LOG.debug("excludeFields:" + excludeFields);
        LOG.debug("filterQueries:" + filterQueries);
        CopyIndex copyIndex = new CopyIndex();
        if (commandLine.hasOption("clean")) {
            destClient.deleteAll();
        }
        copyIndex.setNumThreads(getInt(commandLine, "numThreads", NUM_THREADS));
        copyIndex.setBatchSize(getInt(commandLine, "b", BATCH_SIZE));

        copyIndex.execute(srcClient, destClient, filterQueries, includeFields,
                excludeFields);
    }

    private static Set<String> updateExcludeList(SearchClient srcClient,
                                               Set<String> excludeFields)
            throws IOException, SearchClientException {
        Set<String> tmp = new HashSet<>();
        tmp.addAll(excludeFields);
        tmp.addAll(srcClient.getCopyFields());
        tmp.addAll(srcClient.getSystemInternalFields());
        return tmp;
    }

    private void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    private void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    private static Set<String> splitComma(String s) {
        if (StringUtils.isBlank(s)) {
            return Collections.EMPTY_SET;
        }
        Set<String> ret = new HashSet<>();
        for (String c : s.split(",")) {
            ret.add(c);
        }
        return ret;
    }

    private void execute(SearchClient srcClient, SearchClient destClient,
                         Set<Query> filterQueries,
                         Set<String> includeFields, Set<String> excludeFields)
            throws IOException, SearchClientException {
        ArrayBlockingQueue<Set<String>> idQueue = new ArrayBlockingQueue<>(100);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads + 1);
        ExecutorCompletionService<Integer> executorCompletionService =
                new ExecutorCompletionService<>(executorService);
        String srcIdField = srcClient.getDefaultIdField();
        String destIdField = destClient.getDefaultIdField();

        executorCompletionService.submit(srcClient.getIdGrabber(idQueue,
                batchSize, numThreads, filterQueries));

        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new Copier(
                    idQueue, srcClient, destClient,
                    includeFields, excludeFields));
        }
        int finished = 0;
        try {
            while (finished < numThreads + 1) {
                Future<Integer> future = executorCompletionService.poll(1,
                        TimeUnit.SECONDS);
                if (future != null) {
                    Integer done = null;
                    try {
                        done = future.get();
                    } catch (ExecutionException e) {
                        LOG.error(e);
                    }
                    if (done != null) {
                        if (done < 0) {
                            LOG.debug("id grabber is done");
                        } else if (done > 0) {
                            LOG.debug("finished: " + finished + " : " + done);
                        }
                    }
                    finished++;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        executorService.shutdownNow();
    }

    private static class Copier implements Callable<Integer> {
        private final String srcIdField;
        private final String destIdField;
        private final ArrayBlockingQueue<Set<String>> ids;
        private final SearchClient src;
        private final SearchClient dest;
        private final Set<String> includeFields;
        private final Set<String> excludeFields;
        private int totalDocs = 0;

        private Copier(ArrayBlockingQueue<Set<String>> ids,
                       SearchClient src, SearchClient dest,
                       Set<String> includeFields, Set<String> excludeFields)
                throws IOException, SearchClientException {
            this.srcIdField = src.getDefaultIdField();
            this.destIdField = dest.getDefaultIdField();
            this.ids = ids;
            this.src = src;
            this.dest = dest;
            this.includeFields = includeFields;
            this.excludeFields = excludeFields;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                //block on more ids
                Set<String> myIds = ids.take();
                LOG.debug("ids size: " + ids.size() + " : " + myIds.size());
                if (myIds.size() == 0) {
                    return totalDocs;
                }
                List<StoredDocument> docs = src.getDocs(srcIdField, myIds,
                        includeFields, excludeFields);
                if (!srcIdField.equals(destIdField)) {
                    for (StoredDocument d : docs) {
                        d.rename(srcIdField, destIdField);
                    }
                }

                dest.addDocuments(docs);
                LOG.debug("inserted : " + totalDocs);
                totalDocs += docs.size();
            }

        }
    }
}
