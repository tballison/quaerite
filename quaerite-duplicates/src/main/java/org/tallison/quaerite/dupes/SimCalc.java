package org.tallison.quaerite.dupes;

import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.core.stats.TokenDF;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SimCalc {
    private final SearchClient searchClient;
    private final String field;

    public SimCalc(SearchClient searchClient, String field) {
        this.searchClient = searchClient;
        this.field = field;
    }

    public double jaccard(String contentA, String contentB) throws IOException, SearchClientException {
        Map<String, Integer> tokensA = mapify(contentA);
        Map<String, Integer> tokensB = mapify(contentB);

        int totalA = tokensA.values().stream().reduce(0, Integer::sum);
        int totalB = tokensB.values().stream().reduce(0, Integer::sum);
        int denom = totalA + totalB;
        if (denom == 0) {
            return -1;
        }
        int numerator = 0;
        for (Map.Entry<String, Integer> e : tokensA.entrySet()) {
            if (tokensB.containsKey(e.getKey())) {
                numerator += 2 * Math.min(e.getValue(), tokensB.get(e.getKey()));
            }
        }
        return (double)numerator/(double)denom;
    }

    private Map<String, Integer> mapify(String content) throws IOException, SearchClientException {
        Map<String, Integer> tokens = new HashMap<>();
        for (String token : searchClient.analyze(field, content)) {
            Integer cnt = tokens.get(token);
            if (cnt == null) {
                cnt = 1;
            } else {
                cnt++;
            }
            tokens.put(token, cnt);
        }
        return tokens;
    }
}
