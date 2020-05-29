package org.tallison.quaerite.analysis;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.stats.TokenDF;
import org.tallison.quaerite.core.stats.TokenDFTF;

import java.util.List;

public class CountTokens {

    static Options OPTIONS = new Options();
    static {
        OPTIONS.addOption(
                Option.builder("s")
                        .hasArg()
                        .required()
                        .desc("search server").build()
        );
        OPTIONS.addOption(Option.builder("f")
                .longOpt("field")
                .hasArg(true)
                .required()
                .desc("field").build()
        );
    }

    public static void main(String[] args) throws Exception {
        CommandLine commandLine = null;

        try {
            commandLine = new DefaultParser().parse(OPTIONS, args);
        } catch (ParseException e) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.printHelp(
                    "java -jar org.tallison.quaerite.analysis.CompareAnalyzers",
                    OPTIONS);
            return;
        }
        SearchClient client = SearchClientFactory.getClient(commandLine.getOptionValue("s"));
        String field = commandLine.getOptionValue("f");

        String lower = "";
        int termSetSize = 10000;
        int minDF = 0;
        long uniqueTerms = 0;
        long totalTermCount = 0;
        while (true) {
            List<TokenDF> terms = client.getTerms(field, lower, termSetSize, minDF, true);
            for (TokenDF tdf : terms) {
                totalTermCount += ((TokenDFTF) tdf).getTf();
                uniqueTerms++;
            }
            if (terms.size() == 0) {
                break;
            }
            lower = terms.get(terms.size() - 1).getToken();
        }
        System.out.println("unique terms: "+uniqueTerms);
        System.out.println("total tokens: "+ totalTermCount);
    }
}
