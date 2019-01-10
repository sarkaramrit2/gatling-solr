import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

import java.io.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class QuerySampler {

    final static String zkHost = "localhost:9983";
    final static CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(zkHost).build();
    final static String collection = "wiki";
    final static Random r = new Random();
    final static Set dropWords = new HashSet();

    public static void main(String args[]) throws Exception {

        // stop words to drop words from query sampler, mainly articles, prepositions etc
        File file = new File("/Users/apple/Work/SOLRs/conf/stopwords.txt");
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            dropWords.add(line);
        }
        fileReader.close();

        client.setDefaultCollection(collection);
        for (int x = 0; x < 1000; x++) {

            // create sample queries
            FileWriter fw = new FileWriter("/Users/apple/filter-queries.txt", true);
            // FileWriter fw = new FileWriter("/Users/apple/queries.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter writer = new PrintWriter(bw);

            String query = "*:*";
            Object[] keySet;
            int iters = 5;
            boolean toggle = false;
            int limit = 50;
            int lowerlimit = 25;
            int limit_words = 1;
            while (iters-- > 0) {

                SolrParams params = new ModifiableSolrParams()
                        .add("q", query.trim())
                        .add("rows", "0")
                        .add("facet", "true")
                        .add("facet.field", "title")
                        .add("facet.limit", String.valueOf(limit));

                if (iters != 4) {
                    ((ModifiableSolrParams) params).remove("facet.limit");
                    params = ((ModifiableSolrParams) params).add("facet.limit", String.valueOf(lowerlimit));
                }

                QueryResponse response = client.query(params);
                NamedList responseList = response.getResponse();
                NamedList facet_counts = (NamedList) (responseList.get("facet_counts"));
                NamedList facet_fields = (NamedList) (facet_counts.get("facet_fields"));
                NamedList title_t = (NamedList) (facet_fields.get("title"));
                keySet = title_t.asShallowMap().keySet().toArray();
                int max_words = r.nextInt(limit_words) + 1;
                String terms = "";
                while (max_words > 0) {
                    String value = keySet[r.nextInt(iters != 4 ? lowerlimit : limit)].toString().toLowerCase().trim();
                    if (!value.contains(":") && !dropWords.contains(value) &&
                            !value.matches("[0-9]]") && value.length() > 2) {
                        terms += value + " ";
                        max_words--;
                    }
                }

                params = new ModifiableSolrParams()
                        .add("q", query.trim())
                        .add("rows", "0")
                        .add("facet", "true")
                        .add("facet.field", "description")
                        .add("facet.limit", String.valueOf(limit));

                if (iters != 4) {
                    ((ModifiableSolrParams) params).remove("facet.limit");
                    params = ((ModifiableSolrParams) params).add("facet.limit", String.valueOf(lowerlimit));
                }

                response = client.query(params);
                responseList = response.getResponse();
                facet_counts = (NamedList) (responseList.get("facet_counts"));
                facet_fields = (NamedList) (facet_counts.get("facet_fields"));
                NamedList description = (NamedList) (facet_fields.get("description"));
                keySet = description.asShallowMap().keySet().toArray();
                max_words = r.nextInt(limit_words) + 1;
                while (max_words > 0) {
                    String value = keySet[r.nextInt(iters != 4 ? lowerlimit : limit)].toString().toLowerCase().trim();
                    if (!value.contains(":") && !dropWords.contains(value) &&
                            !value.matches("[0-9]]") && value.length() > 2) {
                        terms += value + " ";
                        max_words--;
                    }
                }
                writer.println(terms.toLowerCase().trim());
                query = "description:" + terms.toLowerCase().trim().split(" ")[0];
            }
            writer.close();
        }
    }
}
