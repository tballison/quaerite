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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


@Disabled("turn into actual unit test")
public class TestJudgmentLoader {
    private static Path DB;

    @BeforeAll
    public static void setUp() throws IOException {
        DB = Files.createTempDirectory("q-tmp-");
    }

    @AfterAll
    public static void tearDown() throws IOException {
        FileUtils.deleteDirectory(DB.toFile());
    }

    @Test
    public void runExperiments() throws Exception {
        Path wd = Paths.get("/home/tallison/chorus/quaerite");
        Path e = wd.resolve("title.json");
        Path judgments = wd.resolve("judgments.csv");
        Path db = wd.resolve("mydb2");
        Path r = wd.resolve("reports");

        QuaeriteCLI.main(new String[]{"RunExperiments",
                "-j", judgments.toString(),
                "-db", db.toString(),
                "-e", e.toString(),
                "-r", r.toString()}
        );
    }

    @Test
    public void findFeatures() throws Exception {
        Path wd = Paths.get("/home/tallison/chorus/quaerite");
        Path judgments = wd.resolve("judgments.csv");
        Path db = wd.resolve("mydb2");

        QuaeriteCLI.main(new String[]{
                "FindFeatures",
                "-j", judgments.toString(),
                "-s", "http://localhost:8983/solr/ecommerce",
                "-f", "name,title,product_type,short_description,ean,search_attributes",
                "-db", db.toString(),
                "-m", "2.0"}
        );
    }
}
