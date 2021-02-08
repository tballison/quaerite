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
package org.tallison.quaerite.core.scorers;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.tallison.quaerite.core.Judgments;
import org.tallison.quaerite.core.SearchResultSet;
import org.tallison.quaerite.core.StoredDocument;


public class NDCG extends DiscountedCumulativeGain2002 {

    static Logger LOG = Logger.getLogger(NDCG.class);

    public NDCG(int atN) {
        super("ndcg", atN);
    }

    @Override
    public double score(Judgments judgments, SearchResultSet searchResultSet) {

        final double idealDCG = calculateIdeal(judgments,
                Math.min(getAtN(), searchResultSet.size()), searchResultSet.getTotalHits(),
                searchResultSet.getQueryTime(),
                searchResultSet.getElapsedTime());
        if (idealDCG == 0) {
            LOG.warn("IdealDCG == 0: (NDCG@" + getAtN() + "): " + judgments.getQueryStrings());
            addScore(judgments.getQueryInfo(), ERROR_VALUE);
            return ERROR_VALUE;
        }
        double score = _score(judgments, searchResultSet) / idealDCG;
        addScore(judgments.getQueryInfo(), score);
        return score;
    }


    private double calculateIdeal(Judgments judgments, int size, long totalHits,
                                  long queryTime, long elapsedTime) {
        List<StoredDocument> bestResults = new ArrayList<>();
        for (String id : judgments.getSortedJudgments().keySet()) {
            StoredDocument sd = new StoredDocument(id);
            bestResults.add(sd);
            if (bestResults.size() >= size) {
                break;
            }
        }
        return _score(judgments, new SearchResultSet(totalHits, queryTime,
                elapsedTime, bestResults));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NDCG)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
