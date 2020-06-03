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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tallison.quaerite.core.QueryStrings;
import org.tallison.quaerite.core.features.MaxDocFreq;
import org.tallison.quaerite.core.features.MaxQueryTerms;
import org.tallison.quaerite.core.features.MaxWordLength;
import org.tallison.quaerite.core.features.MinDocFreq;
import org.tallison.quaerite.core.features.MinTermFreq;
import org.tallison.quaerite.core.features.MinWordLength;

/**
 * This represents ES's MoreLikeThisQuery for now.
 * We may have to make modifications to make it work with Solr going forward.
 *
 * This does not yet support artificial documents
 */
public class MoreLikeThisQuery extends MultiFieldQuery {

    public static String QUERY_STRING_INDEX_NAME = "index";
    public static String QUERY_STRING_ID_NAME = "id";
    public static String QUERY_STRING_TEXT_NAME = "text";

    private static Pattern INDEX_PATTERN = Pattern.compile("\\A" +
            QUERY_STRING_INDEX_NAME + "(\\d+)\\Z");
    private static Pattern ID_PATTERN = Pattern.compile("\\A" +
            QUERY_STRING_ID_NAME + "(\\d+)\\Z");
    private static Pattern TEXT_PATTERN = Pattern.compile("\\A" +
            QUERY_STRING_TEXT_NAME + "(\\d+)\\Z");

    private final List<IndexIdPair> indexIdPairs = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();

    //default values from ES
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-mlt-query.html
    MaxQueryTerms maxQueryTerms = new MaxQueryTerms(25);
    MinTermFreq minTermFreq = new MinTermFreq(2);
    MinDocFreq minDocFreq = new MinDocFreq(5);
    MaxDocFreq maxDocFreq = new MaxDocFreq(Integer.MAX_VALUE);
    MinWordLength minWordLength = new MinWordLength(0);
    MaxWordLength maxWordLength = new MaxWordLength(0);

    public MoreLikeThisQuery() {
        super(null);
    }

    @Override
    public String getName() {
        return "mlt";
    }

    public MaxQueryTerms getMaxQueryTerms() {
        return maxQueryTerms;
    }

    public void setMaxQueryTerms(MaxQueryTerms maxQueryTerms) {
        this.maxQueryTerms = maxQueryTerms;
    }

    public MinTermFreq getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(MinTermFreq minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public MinDocFreq getMinDocFreq() {
        return minDocFreq;
    }

    public void setMinDocFreq(MinDocFreq minDocFreq) {
        this.minDocFreq = minDocFreq;
    }

    public MaxDocFreq getMaxDocFreq() {
        return maxDocFreq;
    }

    public void setMaxDocFreq(MaxDocFreq maxDocFreq) {
        this.maxDocFreq = maxDocFreq;
    }

    public MinWordLength getMinWordLength() {
        return minWordLength;
    }

    public void setMinWordLength(MinWordLength minWordLength) {
        this.minWordLength = minWordLength;
    }

    public MaxWordLength getMaxWordLength() {
        return maxWordLength;
    }

    public void setMaxWordLength(MaxWordLength maxWordLength) {
        this.maxWordLength = maxWordLength;
    }


    @Override
    public MoreLikeThisQuery deepCopy() {
        MoreLikeThisQuery mlt = new MoreLikeThisQuery();
        mlt.setQF(getQF());
        mlt.setMaxDocFreq(getMaxDocFreq().deepCopy());
        mlt.setMaxQueryTerms(getMaxQueryTerms().deepCopy());
        mlt.setMaxWordLength(getMaxWordLength().deepCopy());
        mlt.setMinDocFreq(getMinDocFreq().deepCopy());
        mlt.setMinTermFreq(getMinTermFreq().deepCopy());
        mlt.setMinWordLength(getMinWordLength().deepCopy());
        return mlt;
    }

    /**
     * This updates each clause with the appropriate query string.
     * @param queryStrings
     * @throws IllegalAccessException if there the queryString set does
     * not equal the clauses' queryString set
     */
    @Override
    public Set<String> setQueryStrings(QueryStrings queryStrings) {
        Matcher indexMatcher = INDEX_PATTERN.matcher("");
        Matcher textMatcher = TEXT_PATTERN.matcher("");
        Matcher idMatcher = ID_PATTERN.matcher("");
        Set<String> used = new HashSet<>();
        Map<Integer, String> indexes = new HashMap<>();
        Map<Integer, String> ids = new HashMap<>();
        for (String name : queryStrings.names()) {
            if (indexMatcher.reset(name).find()) {
                int i = Integer.parseInt(indexMatcher.group(1));
                String value = queryStrings.getStringByName(name);
                indexes.put(i, value);
                used.add(name);
            } else if (idMatcher.reset(name).find()) {
                int i = Integer.parseInt(idMatcher.group(1));
                String value = queryStrings.getStringByName(name);
                ids.put(i, value);
                used.add(name);
            } else if (textMatcher.reset(name).find()) {
                texts.add(queryStrings.getStringByName(name));
                used.add(name);
            }
        }
        if (indexes.size() != ids.size()) {
            throw new IllegalArgumentException("Mismatch in number of ids and number of indices!" +
                    " " + ids.size() + " <> " + indexes.size());
        }
        for (int i : indexes.keySet()) {
            if (! ids.containsKey(i)) {
                throw new IllegalArgumentException("Can't find id" +
                        i + " to match index" + i);
            }
            indexIdPairs.add(new IndexIdPair(indexes.get(i), ids.get(i)));
        }

        return used;
    }

    @Override
    public String toString() {
        return "MoreLikeThisQuery{" +
                "indexIdPairs=" + indexIdPairs +
                ", texts=" + texts +
                ", maxQueryTerms=" + maxQueryTerms +
                ", minTermFreq=" + minTermFreq +
                ", minDocFreq=" + minDocFreq +
                ", maxDocFreq=" + maxDocFreq +
                ", minWordLength=" + minWordLength +
                ", maxWordLength=" + maxWordLength +
                ", qf=" + qf +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MoreLikeThisQuery that = (MoreLikeThisQuery) o;
        return Objects.equals(indexIdPairs, that.indexIdPairs) &&
                Objects.equals(texts, that.texts) &&
                Objects.equals(maxQueryTerms, that.maxQueryTerms) &&
                Objects.equals(minTermFreq, that.minTermFreq) &&
                Objects.equals(minDocFreq, that.minDocFreq) &&
                Objects.equals(maxDocFreq, that.maxDocFreq) &&
                Objects.equals(minWordLength, that.minWordLength) &&
                Objects.equals(maxWordLength, that.maxWordLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), indexIdPairs, texts,
                maxQueryTerms, minTermFreq, minDocFreq,
                maxDocFreq, minWordLength, maxWordLength);
    }

    public List<IndexIdPair> getIndexIdPairs() {
        return indexIdPairs;
    }

    public List<String> getTexts() {
        return texts;
    }

    public static class IndexIdPair {
        private final String index;
        private final String id;

        public IndexIdPair(String index, String id) {
            this.index = index;
            this.id = id;
        }

        public String getIndex() {
            return index;
        }

        public String getId() {
            return id;
        }
    }
}
