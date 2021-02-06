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
package org.tallison.quaerite.connectors;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.tallison.quaerite.core.ExperimentConfig;
import org.tallison.quaerite.core.FacetResult;
import org.tallison.quaerite.core.SearchResultSet;
import org.tallison.quaerite.core.StoredDocument;
import org.tallison.quaerite.core.queries.Query;
import org.tallison.quaerite.core.stats.TokenDF;

/**
 * SearchClient represents the basic functionality of a search client.
 * <p>
 * For simplicity with the underlying httpclient, concrete classes
 * of SearchClient should not be considered thread safe and must
 * be created for each thread.
 * </p>
 */
public abstract class SearchClient implements Closeable {

    public abstract SearchResultSet search(QueryRequest query)
            throws SearchClientException, IOException;

    public abstract FacetResult facet(QueryRequest query)
            throws SearchClientException, IOException;

    static Logger LOG = Logger.getLogger(SearchClient.class);

    private final HttpClient httpClient;

    public SearchClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }


    protected byte[] getUrl(String url) throws SearchClientException {
        return HttpUtils.get(httpClient, url);
    }

    protected JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        ByteArrayEntity entity = new ByteArrayEntity(json.getBytes(StandardCharsets.UTF_8));
        httpRequest.setEntity(entity);
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        //At one point, this was required because of connection already
        // bound exceptions on windows :(
        //httpPost.setHeader("Connection", "close");

        //try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

        HttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                try (Reader reader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(),
                                StandardCharsets.UTF_8))) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace(element);
                    }
                    return new JsonResponse(200, element);
                }
            } else {
                return new JsonResponse(status,
                        new String(EntityUtils.toByteArray(response.getEntity()),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (response != null && response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse)response).close();
            }
            httpRequest.releaseConnection();
        }
    }


    protected static String encode(String s) throws IllegalArgumentException {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void close() throws IOException {
        if (httpClient instanceof  CloseableHttpClient) {
            ((CloseableHttpClient)httpClient).close();
        }
    }

    public abstract void addDocuments(List<StoredDocument> buildDocuments)
            throws IOException, SearchClientException;

    public abstract List<StoredDocument> getDocs(String idField, Set<String> ids,
                                                 Set<String> includeFields,
                                                 Set<String> excludeFields)
            throws IOException, SearchClientException;

    /**
     * if not supported, this should return an empty collection
     *
     * @return
     */
    public abstract Collection<? extends String> getCopyFields()
            throws IOException, SearchClientException;

    public String getIdField(ExperimentConfig config)
            throws IOException, SearchClientException {
        if (!StringUtils.isBlank(config.getIdField())) {
            return config.getIdField();
        }
        return getDefaultIdField();
    }

    public abstract String getDefaultIdField() throws IOException, SearchClientException;

    public abstract void deleteAll() throws SearchClientException, IOException;

    public abstract IdGrabber getIdGrabber(ArrayBlockingQueue<Set<String>> ids,
                                           int batchSize,
                                           int copierThreads,
                                           Collection<Query> filterQueries)
            throws IOException, SearchClientException;


    protected JsonResponse getJson(String url) throws IOException,
            SearchClientException {
        byte[] bytes;
        try {
            bytes = getUrl(url);
        } catch (SearchClientException e) {
            return new JsonResponse(-1, e.getMessage());
        }
        try (Reader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8))) {
            return new JsonResponse(200, JsonParser.parseReader(reader));
        }
    }

    /**
     * return common system internal fields, such as "_version_"
     * in Solr
     *
     * @return
     */
    public abstract Set<String> getSystemInternalFields();

    public abstract List<String> analyze(String field, String string)
            throws IOException, SearchClientException;

    public abstract List<TokenDF> getTerms(String field, String lower,
                                           int limit, int minCount, boolean includeTf)
            throws IOException, SearchClientException;
}
