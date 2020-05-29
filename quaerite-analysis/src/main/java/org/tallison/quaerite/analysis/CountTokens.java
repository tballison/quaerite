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

import java.util.List;

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


public class CountTokens {

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

        String lower = "";
        int termSetSize = 10000;
        int minDF = 0;
        long uniqueTerms = 0;
        long totalTermCount = 0;
        long alphabeticUnique = 0;
        long alphabeticTotal = 0;
        while (true) {
            List<TokenDF> terms = client.getTerms(field, lower, termSetSize, minDF, true);
            for (TokenDF tdf : terms) {
                boolean hasAlphabetic = false;
                for (int cp : tdf.getToken().codePoints().toArray()) {
                    if (Character.isAlphabetic(cp)) {
                        hasAlphabetic = true;
                        break;
                    }
                }
                totalTermCount += ((TokenDFTF) tdf).getTf();
                uniqueTerms++;
                if (hasAlphabetic) {
                    alphabeticTotal += ((TokenDFTF)tdf).getTf();
                    alphabeticUnique++;
                }
            }
            if (terms.size() == 0) {
                break;
            }
            lower = terms.get(terms.size() - 1).getToken();
        }
        System.out.println("unique terms: " + uniqueTerms);
        System.out.println("total tokens: " + totalTermCount);
        System.out.println("unique alphabetic terms: " + alphabeticUnique);
        System.out.println("total alphabetic tokens: " + alphabeticTotal);
    }
}
