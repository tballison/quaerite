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
package org.tallison.quaerite.dupes;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.tallison.quaerite.connectors.ESClient;
import org.tallison.quaerite.connectors.QueryRequest;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.SearchResultSet;
import org.tallison.quaerite.core.ServerConnection;
import org.tallison.quaerite.core.StoredDocument;
import org.tallison.quaerite.core.queries.LuceneQuery;
import org.tallison.quaerite.core.queries.MatchAllDocsQuery;
import org.tallison.quaerite.core.queries.Query;

public class IndexCopier {

    public static void main(String[] args) throws Exception {
        ServerConnection src = new ServerConnection(args[0], args[1], args[2]);
        ServerConnection targ = new ServerConnection(args[3], args[4], args[5]);

        int numThreads = 50;
        IndexCopier copier = new IndexCopier();
        copier.execute(src, targ, numThreads);

    }

    private void execute(ServerConnection srcConnection, ServerConnection targConnection, int numThreads) throws IOException, SearchClientException {
//        Query q = new LuceneQuery("content", "\"daisy chain boards\"");// MatchAllDocsQuery();
        Query q = new MatchAllDocsQuery();
        QueryRequest req = new QueryRequest(q);
        req.addFieldsToRetrieve("content", "digest", "domain", "url");
        req.setSort("date", QueryRequest.SORT_ORDER.DESC);
        long start = System.currentTimeMillis();
        ESClient srcClient = (ESClient)SearchClientFactory.getClient(srcConnection);
        //single threaded scroll -- consider slicing it?
        SearchResultSet rs = srcClient.startScroll(req, 1000, 2);
        String scrollId = rs.getScrollId();
        int totalProcessed = 0;
        List<ESClient> targClients = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            targClients.add((ESClient)SearchClientFactory.getClient(targConnection));
        }
        while (rs.size() > 0) {
            processResultSet(rs, targClients);
            totalProcessed += rs.size();
            System.out.println(totalProcessed);
            rs = srcClient.scrollNext(scrollId, 2);
        }
        long elapsed = System.currentTimeMillis()-start;
        System.out.println("elapsed: "+elapsed);
    }

    private void processResultSet(SearchResultSet rs, List<ESClient> targClients) throws IOException, SearchClientException {
        int numThreads = targClients.size();
        ArrayBlockingQueue<StoredDocument> docs = new ArrayBlockingQueue<>(rs.size()+numThreads);
        ArrayBlockingQueue<StoredDocument> targs = new ArrayBlockingQueue<>(rs.size());


        for (int i = 0; i < rs.size(); i++) {
            docs.add(rs.get(i));
        }
        for (int i = 0; i < numThreads; i++) {
            docs.add(new PoisonDocument("-1"));
        }
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(executorService);

        for (int i = 0; i < numThreads; i++) {
            completionService.submit(new DocMapper(docs, targs, targClients.get(i)));
        }

        int completed = 0;
        while (completed < numThreads) {
            Future<Integer> future = completionService.poll();
            if (future != null) {
                try {
                    completed++;
                    future.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

        executorService.shutdownNow();
        List<StoredDocument> targList = new ArrayList<>();
        targList.addAll(targs);
        targClients.get(0).addDocuments(targList);
    }

    private static class DocMapper implements Callable<Integer> {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        int id = COUNTER.getAndIncrement();
        private final ArrayBlockingQueue<StoredDocument> source;
        private final ArrayBlockingQueue<StoredDocument> target;
        private final ESClient client;

        private DocMapper(ArrayBlockingQueue<StoredDocument> source,
                          ArrayBlockingQueue<StoredDocument> target,
                          ESClient client) {
            this.source = source;
            this.target = target;
            this.client = client;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                StoredDocument sd = source.take();
                if (sd == null || sd instanceof PoisonDocument) {
                    return 1;
                }
                target.add(map(sd, client));
            }
        }


        private StoredDocument map(StoredDocument src, ESClient client) {
            String content = (String) src.getFields().get("content");
            content = (content == null) ? "" : content;
            String digest = (String) src.getFields().get("digest");
            String host = (String) src.getFields().get("host");
            String url = (String) src.getFields().get("url");
            StoredDocument copy = new StoredDocument(src.getId());

            copy.addNonBlankField("idx", src.getIndex());
            copy.addNonBlankField("content", content);
            copy.addNonBlankField("digest", digest);
            host = getHost(host, url);
            copy.addNonBlankField("host", host);
            copy.addNonBlankField("url", url);
            copy.addNonBlankField("sha256", DigestUtils.sha256Hex(content));
            List<String> tokens = null;
            try {
                tokens = client.analyze("content", content);
            } catch (SearchClientException e) {
                tokens = Collections.EMPTY_LIST;
            }
            //System.out.println(src.getId());
            /*if (tokens.size() > 3) {
                System.out.println("TOKENS: "+id + " : "
                        + tokens.get(0) + " "+tokens.get(1)+
                        " " + tokens.get(2));
            }*/
            StringBuilder analyzed = new StringBuilder();
            for (String t : tokens) {
                analyzed.append(t).append(" ");
            }
            copy.addNonBlankField("sha256_analyzed",
                    DigestUtils.sha256Hex(analyzed.toString().trim()));

            return copy;
        }

        private String getHost(String host, String url) {
            if (!StringUtils.isAllBlank(host)) {
                return host;
            }

            try {
                URL u = new URL(url);
                return u.getHost();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return "";
        }


    }

    private class PoisonDocument extends StoredDocument {

        public PoisonDocument(String id) {
            super(id);
        }
    }
}
