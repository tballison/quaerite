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
package org.mitre.quaerite.cli;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.mitre.quaerite.Experiment;
import org.mitre.quaerite.ExperimentFeatures;
import org.mitre.quaerite.ExperimentSet;
import org.mitre.quaerite.features.Feature;
import org.mitre.quaerite.features.FeatureSet;
import org.mitre.quaerite.features.FeatureSets;
import org.mitre.quaerite.features.URLS;
import org.mitre.quaerite.scorecollectors.ScoreCollector;

public class GenerateExperiments {

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("i")
                        .longOpt("input_features")
                        .hasArg()
                        .desc("experiment features json file")
                        .required().build()
        );
        OPTIONS.addOption(
                Option.builder("o")
                        .longOpt("output_experiments")
                        .hasArg()
                        .desc("experiments file")
                        .required().build()
        );
    }
    private int experimentCount = 0;
    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.mitre.quaerite.cli.GenerateExperiments",
                    OPTIONS);
            return;
        }

        Path input = Paths.get(commandLine.getOptionValue('i'));
        Path output = Paths.get(commandLine.getOptionValue("o"));
        GenerateExperiments generateExperiments = new GenerateExperiments();
        generateExperiments.execute(input, output);
    }

    private void execute(Path input, Path output) throws Exception {
        ExperimentFeatures experimentFeatures = null;

        try (Reader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
             experimentFeatures = ExperimentFeatures.fromJson(reader);
        }
        ExperimentSet experimentSet = new ExperimentSet();
        for (ScoreCollector scoreCollector : experimentFeatures.getScoreCollectors()) {
            experimentSet.addScoreCollector(scoreCollector);
        }
        FeatureSets featureSets = experimentFeatures.getFeatureSets();

        Set<String> featureKeySet = featureSets.keySet();
        List<String> featureKeys = new ArrayList<>(featureKeySet);
        Map<String, Set<Feature>> instanceFeatures = new HashMap<>();
        recurse(0, featureKeys, featureSets, instanceFeatures, experimentSet);

        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(experimentSet.toJson());
            writer.flush();
        }
    }

    private void recurse(int i, List<String> featureKeys, FeatureSets featureSets, Map<String, Set<Feature>> instanceFeatures, ExperimentSet experimentSet) {
        if (i >= featureKeys.size()) {
            addExperiments(instanceFeatures, experimentSet);
            return;
        }
        String featureName = featureKeys.get(i);
        FeatureSet featureSet = featureSets.get(featureName);
        boolean hadContents = false;
        for (Set<Feature> set : featureSet.permute(1000)) {
            instanceFeatures.put(featureName, set);
            recurse(i+1, featureKeys, featureSets, instanceFeatures, experimentSet);
            hadContents = true;
        }
        if (! hadContents) {
            recurse(i+1, featureKeys, featureSets, instanceFeatures, experimentSet);
        }
    }

    private void addExperiments(Map<String, Set<Feature>> features,
                                ExperimentSet experimentSet) {
        String experimentName = "experiment_"+experimentCount++;
        String searchServerUrl = getOnlyString(features.get("urls"));
        String customHandler = getOnlyString(features.get("customHandlers"));

        Experiment experiment = (customHandler == null) ?
                new Experiment(experimentName, searchServerUrl) :
                new Experiment(experimentName, searchServerUrl, customHandler);
        for (Map.Entry<String, Set<Feature>> e : features.entrySet()) {
            if (!e.getKey().equals("urls") && !e.getKey().equals("customHandlers")) {
                for (Feature f : e.getValue()) {
                    experiment.addParam(e.getKey(), f.toString());
                }
            }
        }
        experimentSet.addExperiment(experimentName, experiment);
    }

    private String getOnlyString(Set<Feature> features) {
        if (features == null) {
            return null;
        }
        if (features.size() != 1) {
            throw new IllegalArgumentException("features must have only one value: "+ features.size());
        }
        for (Feature f : features) {
            return f.toString();
        }
        return "";
    }

    private class NamedFeatureSet {
        String name;
        FeatureSet featureSet;
    }


}