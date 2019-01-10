import java.io.*;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class SolrFacetQueryCreator {

    final static Random r = new Random();
    final static Set filterQueries = new HashSet();
    final static Set queries = new HashSet();

    public static void main(String args[]) throws Exception {

        File filterQueriesFile = new File("/Users/apple/filter-queries.txt");
        FileReader fileReader = new FileReader(filterQueriesFile);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            filterQueries.add(line);
        }
        fileReader.close();

        File querieFile = new File("/Users/apple/queries.txt");
        fileReader = new FileReader(querieFile);
        bufferedReader = new BufferedReader(fileReader);
        while ((line = bufferedReader.readLine()) != null) {
            queries.add(line);
        }
        fileReader.close();

        String allQ = "*:*";
        Object[] filters = filterQueries.toArray();
        Object[] qs = queries.toArray();

        FileWriter fw = new FileWriter("/Users/apple/Downloads/solr-gatling/src/test/resources/data/solrFacetQueries.tsv", true);
        BufferedWriter bw = new BufferedWriter(fw);
        PrintWriter writer = new PrintWriter(bw);
        writer.println("id" + '\t' + "params");

        for (int x = 0; x < 5000; x++) {

            String query = "q=";
            query += qs[r.nextInt(qs.length)];
            if (r.nextBoolean()) {
                query += "&fq=";
                if (r.nextBoolean()) {
                    query += "title:(" + filters[r.nextInt(filters.length)];
                } else {
                    query += "description:(" + filters[r.nextInt(filters.length)];
                }
                query += ")";
            }

            int rows = 10;
            if (r.nextBoolean()) {
                rows = Math.abs(r.nextInt(10) * 10) + r.nextInt(10);
            }

            int start = 0;
            if (r.nextBoolean()) {
                start = Math.abs(rows / (r.nextInt(r.nextInt(10) + 1) + 1));
            }
            query += "&rows=" + rows + "&start=" + start;

            if(r.nextBoolean()) {
                query += "&sort=";
                if (r.nextBoolean()) {
                    query += "title_t_sort";
                }
                else {
                    query += "description_t_sort";
                }
                if (r.nextBoolean()) {
                    query += " asc";
                }
                else {
                    query += " desc";
                }
            }

            String json_string = "&" +
                    "json.facet={" +
                    "titles : {" +
                    "type : terms," +
                    "field : " + (r.nextBoolean() ? "title_t_sort" : "description_t_sort") + "," +
                    "limit : " + (String.valueOf(r.nextInt(
                            r.nextInt(100) + 1) + 1)) + "," +
                    "mincount : " + (String.valueOf(r.nextInt(
                            r.nextInt(10) + 1) + 1)) +
                    "}" +
                    "}";

            query += json_string;

            writer.println(String.valueOf(x) + '\t' + query);
        }
        writer.close();
    }
}
