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
package org.tallison.quaerite.examples;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.StoredDocument;


/**
 * Simple indexer to index a utf-8 text file with one document per line.
 * Default field is "content".  Specify your content field with -f
 */
public class IndexRows {

    private final int DOCS_PER_BATCH = 1000;

    static Options OPTIONS = new Options();

    static {
        OPTIONS.addOption(
                Option.builder("i")
                        .required()
                        .hasArg()
                        .desc("input file").build());
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg()
                        .required()
                        .desc("search system url").build());
        OPTIONS.addOption(
                Option.builder("f")
                        .hasArg()
                        .required()
                        .desc("field").build());
    }

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
        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        Path file = Paths.get(commandLine.getOptionValue("i"));
        String field = (commandLine.hasOption("f")) ? commandLine.getOptionValue("f") : "content";
        IndexRows indexer = new IndexRows();
        indexer.execute(file, field, client);
    }

    private void execute(Path file, String field, SearchClient client) throws Exception {
        List<StoredDocument> docs = new ArrayList<>();
        int counter = 0;
        try (BufferedReader bufferedReader = getReader(file)) {
            String line = bufferedReader.readLine();
            while (line != null) {
                StoredDocument sd = new StoredDocument(Integer.toString(counter++));
                sd.addNonBlankField(field, line);
                docs.add(sd);
                if (docs.size() > DOCS_PER_BATCH) {
                    client.addDocuments(docs);
                    docs.clear();
                }
                line = bufferedReader.readLine();
            }
        }
        if (docs.size() > 0) {
            client.addDocuments(docs);
        }
    }

    private BufferedReader getReader(Path file) throws IOException {
        if (file.getFileName().toString().endsWith(".gz")) {
            return new BufferedReader(
                    new InputStreamReader(
                            new GzipCompressorInputStream(
                                Files.newInputStream(file)
                            ),
                            StandardCharsets.UTF_8
                    )
            );
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }
}
