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

import static org.tallison.quaerite.core.util.CommandLineUtil.getBoolean;
import static org.tallison.quaerite.core.util.CommandLineUtil.getPath;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.core.Experiment;
import org.tallison.quaerite.core.ExperimentConfig;
import org.tallison.quaerite.core.ExperimentSet;
import org.tallison.quaerite.db.ExperimentDB;

public class RunExperiments extends AbstractExperimentRunner {

    public static String DEFAULT_ID_FIELD = "id";

    static Logger LOG = LogManager.getLogger(RunExperiments.class);

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("db")
                        .hasArg()
                        .required()
                        .desc("database folder (required)").build()
        );
        OPTIONS.addOption(
                Option.builder("e")
                        .longOpt("experiments")
                        .hasArg(true)
                        .required(true)
                        .desc("experiments .json file)").build()
        );

        OPTIONS.addOption(
                Option.builder("r")
                        .longOpt("reportsDir")
                        .hasArg()
                        .required(false)
                        .desc("directory for reports (optional; default 'reports'").build()
        );

        OPTIONS.addOption(
                Option.builder("j")
                        .longOpt("judgments")
                        .hasArg(true)
                        .required(true)
                        .desc("judgment .csv file (may contain " +
                                "queries only...no judgments").build()
        );

        OPTIONS.addOption(
                Option.builder("freshStart")
                        .required(false)
                        .hasArg(false)
                        .desc("delete all existing scores (optional; " +
                                "used only in iterative mode)").build()
        );

        OPTIONS.addOption(
                Option.builder("x")
                        .longOpt("experiment")
                        .required(false)
                        .hasArg()
                        .desc("run an already loaded experiment by name (optional; " +
                                "default=all)").build()
        );

        OPTIONS.addOption(
                Option.builder("l")
                        .longOpt("latest")
                        .hasArg(false)
                        .required(false)
                        .desc("run the most recently added experiment (optional; " +
                                "default=false)").build()
        );

        OPTIONS.addOption(
                Option.builder("test")
                        .hasArg(false)
                        .required(false)
                        .desc("use this if running a test set; " +
                                "this will sort results by desc order " +
                                "of the test scorer").build()
        );
    }

    long batchStart = -1l;

    public RunExperiments() {
        super(new ExperimentConfig());
    }

    public RunExperiments(ExperimentConfig experimentConfig) {
        super(experimentConfig);
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp("java -jar org.tallison.eval.RunExperiments", OPTIONS);
            return;
        }

        Path dbDir = Paths.get(commandLine.getOptionValue("db"));
        String experimentName = (commandLine.hasOption("experiment")) ?
                commandLine.getOptionValue("experiment") : "";
        boolean freshStart = getBoolean(commandLine, "freshStart");
        boolean latest = getBoolean(commandLine, "latest");
        boolean isTest = getBoolean(commandLine, "test");

        Path judgments = getPath(commandLine, "j", true);
        Path experiments = getPath(commandLine, "e", false);
        Path reportDir = getPath(commandLine, "r", false);
        reportDir = (reportDir == null) ? Paths.get(DumpResults.DEFAULT_REPORT_DIR) :
                reportDir;
        RunExperiments runExperiments = new RunExperiments();

        //TODO: this should be optimized to handle a single experiment
        //we are currently loading all the experiments.
        ExperimentSet experimentSet = null;
        try (ExperimentDB experimentDB = ExperimentDB.open(dbDir)) {
            if (judgments != null && experiments != null) {
                QueryLoader.loadJudgments(experimentDB, judgments, true);
                experimentSet = addExperiments(experimentDB, experiments, false,
                        true);
                runExperiments = new RunExperiments(experimentSet.getExperimentConfig());
                freshStart = false;

            }
            runExperiments.run(experimentSet, experimentDB, experimentName,
                    freshStart, latest);


            LOG.info("starting to write reports to: " + reportDir);
            dumpResults(experimentSet, experimentDB, experimentDB.getQuerySets(),
                    experimentDB.getExperiments().getScorers(), reportDir, isTest);
        }
        LOG.info("completed running and reporting experiments");
    }

    private void run(ExperimentSet experimentSet, ExperimentDB experimentDB,
                     String experimentName, boolean freshStart, boolean latest)
            throws SQLException, IOException, SearchClientException {
        if (freshStart) {
            experimentDB.clearScores();
        }

        if (latest) {
            experimentName = experimentDB.getLatestExperiment();
            experimentDB.clearScores(experimentName);
        }

        if (StringUtils.isBlank(experimentName)) {
            batchStart = System.currentTimeMillis();
            int finished = 0;

            for (Experiment ex : experimentSet.getExperiments().values()) {
                LOG.info("running experiment: '" + ex.getName() + "'");
                runExperiment(ex, experimentSet.getScorers(), experimentSet.getMaxRows(),
                        experimentDB,
                        experimentDB.getJudgments(),
                        "train", true);
                long elapsed = System.currentTimeMillis() - batchStart;
                finished++;
                LOG.info("Finished " + finished + " in " +
                        (double) elapsed / (double) 1000 + " seconds");
                double perExperiment = (double) elapsed / (double) finished;
                int togo = experimentSet.getExperiments().entrySet().size() - finished;
                if (togo > 0) {
                    LOG.info("Still have " + togo + " to go; estimate: " +
                            threePlaces.format(((double) togo * perExperiment) /
                                    (double) 1000) + " seconds\n\n");
                }
            }
        } else {
            Experiment experiment = experimentSet.getExperiment(experimentName);
            if (experiment == null) {
                LOG.warn("I'm sorry, but I couldn't find this experiment:" +
                        experimentName);
                return;
            }
            experimentDB.clearScores(experimentName);

            runExperiment(experiment, experimentSet.getScorers(),
                    experimentSet.getMaxRows(), experimentDB, experimentDB.getJudgments(),
                    "train", true);
        }
    }
}
