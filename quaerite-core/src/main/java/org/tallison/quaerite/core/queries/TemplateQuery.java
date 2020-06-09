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
package org.tallison.quaerite.core.queries;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Query templates for ES.
 *
 * This cannot yet be used in generation of features.  Rather
 * it is currently only designed to be used in Experiments
 *
 * User can specify "params" map in the experiment definition.
 *
 * "from", "to" and "keywords" are populated externally
 */
public class TemplateQuery extends SingleStringQuery {

    private final String id;
    Map<String, String> params = new HashMap<>();

    public TemplateQuery(String id, String queryString) {
        super(queryString);
        this.id = id;
    }

    public void putParam(String key, String value) {
        params.put(key, value);
    }
    @Override
    public String getName() {
        return "template";
    }

    @Override
    public Object deepCopy() {
        TemplateQuery cp = new TemplateQuery(id, getQueryString());
        for (String k : params.keySet()) {
            cp.putParam(k, params.get(k));
        }
        return cp;
    }

    public String getTemplateId() {
        return id;
    }

    public Map<String, String> getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TemplateQuery that = (TemplateQuery) o;
        return id.equals(that.id) &&
                params.equals(that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, params);
    }
}
