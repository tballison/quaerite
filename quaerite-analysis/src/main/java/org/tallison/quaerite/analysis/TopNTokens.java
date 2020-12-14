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
package org.tallison.quaerite.analysis;


import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.stats.TokenDF;
import org.tallison.quaerite.core.stats.TokenDFTF;


public class TopNTokens {

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg()
                        .required()
                        .desc("search server").build()
        );
        OPTIONS.addOption(Option.builder("f")
                .longOpt("field")
                .hasArg(true)
                .required()
                .desc("field").build()
        );
        OPTIONS.addOption(Option.builder("n")
                .longOpt("topN")
                .hasArg(true)
                .required()
                .desc("'n' most frequent tokens to record").build()
        );
        OPTIONS.addOption(Option.builder("m")
                .longOpt("minDF")
                .hasArg(true)
                .required()
                .desc("minimum document frequency").build()
        );
        OPTIONS.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg(true)
                .required()
                .desc("file to which to write results").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.tallison.quaerite.analysis.CompareAnalyzers",
                    OPTIONS);
            return;
        }
        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        String field = commandLine.getOptionValue("f");

        int n = Integer.parseInt(commandLine.getOptionValue("n"));

        String lower = "";
        int termSetSize = 100000;
        int minDF = Integer.parseInt(commandLine.getOptionValue("m"));
        long uniqueTerms = 0;
        long totalTermCount = 0;
        PriorityQueue<TokenDF> queue = new PriorityQueue<>(n, new DFComparator());

        int scanned = 0;
        int maxScanned = -1;//100000;
        boolean hitMax = false;
        while (!hitMax) {
            List<TokenDF> terms = client.getTerms(field, lower,
                    termSetSize, minDF, true);
            for (TokenDF tdf : terms) {
                add(tdf, queue, n);
                totalTermCount += ((TokenDFTF) tdf).getTf();
                uniqueTerms++;
                if (maxScanned > 0 && scanned++ > maxScanned) {
                    hitMax = true;
                    break;
                }
            }
            if (terms.size() == 0) {
                break;
            }

            lower = terms.get(terms.size() - 1).getToken();
        }

        //priority queue is sorted from head which is the "least common"
        //we have to flip it to get descending order.
        List<TokenDF> tokens = new ArrayList<>();
        while (! queue.isEmpty()) {
            tokens.add(queue.remove());
        }
        if (commandLine.hasOption("o")) {
            writeResults(commandLine.getOptionValue("o"), uniqueTerms, totalTermCount, tokens);
        }
        System.out.println("unique terms: " + uniqueTerms);
        System.out.println("total tokens: " + totalTermCount);

        for (int i = tokens.size() - 1; i >= 0; i--) {
            TokenDF tdf = tokens.get(i);
            System.out.println(tdf.getToken().replaceAll("\t", " ")
                    + "\t" + tdf.getDf());
        }

    }

    private static void writeResults(String outputPath,
                                     long uniqueTerms, long totalTermCount,
                                     List<TokenDF> tokens) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                Paths.get(outputPath), StandardCharsets.UTF_8)) {
            writer.write("unique terms: " + uniqueTerms);
            writer.newLine();
            writer.write("total tokens: " + totalTermCount);
            writer.newLine();
            for (int i = tokens.size() - 1; i >= 0; i--) {
                TokenDF tdf = tokens.get(i);
                writer.write(tdf.getToken().replaceAll("\t", " ")
                        + "\t" + tdf.getDf());
                writer.newLine();
            }
        }
    }

    private static void add(TokenDF tdf, PriorityQueue<TokenDF> queue, int max) {

        if (queue.size() < max) {
            queue.add(tdf);
        } else if (queue.comparator().compare(tdf, queue.peek()) > 0) {

            queue.add(tdf);
            while (queue.size() > max) {
                TokenDF toRemove = queue.poll();
            }
        }
    }

    //sort by descending order of frequency and ascending order of term
    private static class DFComparator implements Comparator<TokenDF> {

        @Override
        public int compare(TokenDF o1, TokenDF o2) {
            if (o1.getDf() == o2.getDf()) {
                return o2.getToken().compareTo(o1.getToken());
            } else {
                return (Long.compare(o1.getDf(), o2.getDf()));
            }
        }
    }
}
