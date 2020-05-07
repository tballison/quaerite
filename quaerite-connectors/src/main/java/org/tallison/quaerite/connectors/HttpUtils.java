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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

public class HttpUtils {

    static Logger LOG = Logger.getLogger(HttpUtils.class);

    public static byte[] get(HttpClient httpClient, String url) throws SearchClientException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        HttpGet httpGet = null;
        try {
            String get = uri.getPath();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?" + uri.getRawQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            throw new IllegalArgumentException(url, e);
        }

        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(target, httpGet);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                String msg = new String(EntityUtils.toByteArray(
                        httpResponse.getEntity()), StandardCharsets.UTF_8);
                throw new SearchClientException("Bad status code: " +
                        httpResponse.getStatusLine().getStatusCode()
                        + "for url: " + url + "; msg: " + msg);
            }
            return EntityUtils.toByteArray(httpResponse.getEntity());

        } catch (IOException e) {
            throw new SearchClientException(url, e);
        } finally {
            if (httpResponse != null && httpResponse instanceof CloseableHttpResponse) {
                try {
                    ((CloseableHttpResponse) httpResponse).close();
                } catch (IOException e) {
                    throw new SearchClientException(url, e);
                }
            }
        }
    }

    public static HttpClient getClient(String url,
                                       String username, String password)
            throws SearchClientException {

        String scheme = null;
        try {
            scheme = new URI(url).getScheme();
        } catch (URISyntaxException e) {
            throw new SearchClientException(e);
        }
        if (scheme.endsWith("s")) {
            try {
                return httpClientTrustingAllSSLCerts2(username, password);
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                throw new SearchClientException(e);
            }
        } else if (username != null && password != null) {
            CredentialsProvider provider = getProvider(username, password);
            return HttpClientBuilder.create()
                    .setKeepAliveStrategy(getDefaultKeepAliveStrategy())
                    .setDefaultCredentialsProvider(provider)
                    .build();
        } else {
            return HttpClientBuilder.create()
                    .setKeepAliveStrategy(getDefaultKeepAliveStrategy())
                    .build();
        }
    }


    public static HttpClient getClient(String authority) throws SearchClientException {
        return getClient(authority, null, null);
    }

    private static HttpClient httpClientTrustingAllSSLCerts2(String username,
                                                             String password)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {
        LOG.warn("quaerite currently uses a non-secure 'trustall' client for https." +
                " If you require actual security, please open a ticket " +
                "or initialize the search client with a secure httpclient.");
        CredentialsProvider provider = getProvider(username, password);
        TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null,
                acceptingTrustStrategy).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
                NoopHostnameVerifier.INSTANCE);

        Registry<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("https", sslsf)
                        .register("http", new PlainConnectionSocketFactory())
                        .build();

        BasicHttpClientConnectionManager connectionManager =
                new BasicHttpClientConnectionManager(socketFactoryRegistry);
        if (provider == null) {
            return HttpClients.custom()
                    .setKeepAliveStrategy(getDefaultKeepAliveStrategy())
                    .setSSLSocketFactory(sslsf)
                    .setConnectionManager(connectionManager)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();

        } else {
            return HttpClients.custom()
                    .setKeepAliveStrategy(getDefaultKeepAliveStrategy())
                    .setSSLSocketFactory(sslsf)
                    .setConnectionManager(connectionManager)
                    .setDefaultCredentialsProvider(provider)
                    .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();
        }
    }

    /**
     * can return null if username and password are both null
     *
     * @param username
     * @param password
     * @return
     */
    private static CredentialsProvider getProvider(String username, String password) {
        if ((username == null && password != null) ||
                (password == null && username != null)) {
            throw new IllegalArgumentException("can't have one of 'username', " +
                    "'password' null and the other not");
        }
        if (username != null && password != null) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            UsernamePasswordCredentials credentials
                    = new UsernamePasswordCredentials(username, password);
            provider.setCredentials(AuthScope.ANY, credentials);
            return provider;
        }
        return null;
    }

    //if no keep-alive header is found or a bad value is present,
    // keep alive for only 5 seconds!
    private static ConnectionKeepAliveStrategy getDefaultKeepAliveStrategy() {
        return new ConnectionKeepAliveStrategy() {

            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                // Honor 'keep-alive' header
                HeaderElementIterator it = new BasicHeaderElementIterator(
                        response.headerIterator(HTTP.CONN_KEEP_ALIVE));
                while (it.hasNext()) {
                    HeaderElement he = it.nextElement();
                    String param = he.getName();
                    String value = he.getValue();
                    if (value != null && param != null &&
                            param.equalsIgnoreCase("timeout")) {
                        try {
                            return Long.parseLong(value) * 1000;
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
                return 5 * 1000;
            }

        };
    }

}
