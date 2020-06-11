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

import java.util.Map;

import com.google.gson.JsonObject;
import org.apache.http.client.HttpClient;
import org.tallison.quaerite.core.util.JsonUtil;

public class ES6Client extends ESClient {

    public ES6Client(String url, HttpClient httpClient) {
        super(url, httpClient);
    }

    protected long getTotalHits(JsonObject hits) {
        return JsonUtil.getPrimitive(hits, "total", -1l);
    }

    @Override
    void trackTotalHits(Map<String, Object> map, boolean b) {
        //ES6 and lower doesn't allow this.
        //silently skip
    }
}
