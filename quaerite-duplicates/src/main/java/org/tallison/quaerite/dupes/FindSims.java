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

import org.tallison.quaerite.connectors.QueryRequest;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.QueryStrings;
import org.tallison.quaerite.core.SearchResultSet;
import org.tallison.quaerite.core.ServerConnection;
import org.tallison.quaerite.core.StoredDocument;
import org.tallison.quaerite.core.features.MaxQueryTerms;
import org.tallison.quaerite.core.features.MinTermFreq;
import org.tallison.quaerite.core.features.QF;
import org.tallison.quaerite.core.features.QueryOperator;
import org.tallison.quaerite.core.features.WeightableField;
import org.tallison.quaerite.core.queries.LuceneQuery;
import org.tallison.quaerite.core.queries.MatchAllDocsQuery;
import org.tallison.quaerite.core.queries.MoreLikeThisQuery;
import org.tallison.quaerite.core.queries.Query;

import java.io.IOException;
import java.util.Collections;
import java.util.Random;

public class FindSims {

    public static void main(String[] args) throws Exception {
        String url = args[0];
        String user = args[1];
        String pw = args[2];

        SearchClient client = SearchClientFactory.getClient(new ServerConnection(url, user, pw));
        Query q = new LuceneQuery("content", "\"error page microstrategy error page\"");
        QueryRequest request = new QueryRequest(q);
        request.addFieldsToRetrieve("_id", "url", "content", "title", "digest");
        request.setNumResults(1000);
        SearchResultSet baseResultSet;

        SimCalc simCalc = new SimCalc(client,"content");
        baseResultSet = client.search(request);
        System.out.println("total hits: " +baseResultSet.getTotalHits());
        for (int i = 0; i < baseResultSet.size(); i++) {
            StoredDocument baseDocument = baseResultSet.get(i);
            MoreLikeThisQuery mlt = new MoreLikeThisQuery();
            mlt.setMinTermFreq(new MinTermFreq(1));
            mlt.setMaxQueryTerms(new MaxQueryTerms(512));
            mlt.setQueryOperator(new QueryOperator(QueryOperator.OPERATOR.OR, 0.9f));

            QF qf = new QF();
            qf.add(new WeightableField("fingerprint"));
            mlt.setQF(qf);
            QueryStrings queryStrings = new QueryStrings();
            //System.out.println(baseDocument.getId());

            queryStrings.addQueryString("id1", baseDocument.getId());
            queryStrings.addQueryString("index1", "tim_dupes_full");
            mlt.setQueryStrings(queryStrings);
            QueryRequest mltRequest = new QueryRequest(mlt);
            mltRequest.addFieldsToRetrieve("_id", "content", "url");
            SearchResultSet rs;
            try {
                rs = client.search(mltRequest);
                System.out.println(i + " " +rs.size());
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            if (rs.size() > 0) {

                System.out.println(rs.getTotalHits() + " : "
                        + baseDocument.getFields().get("url") + "\t"+
                        baseDocument.getFields().get("digest"));
                String content = (String) baseDocument.getFields().get("content");
                for (int j = 0; j < rs.size(); j++) {
                    StoredDocument sd = rs.get(j);
                    System.out.print("\t" +
                            simCalc.jaccard(content, (String) sd.getFields().get("content")));
                    //System.out.print("\t" + sd.getId());
                    //System.out.println("\t" + sd.getFields().get("url")+"\t"+
                    System.out.println("\t"+baseDocument.getFields().get("digest"));
                }
            }
        }

    }
}
