package org.apache.solr;

import com.lucidworks.cloud.OAuth2HttpRequestInterceptor;
import com.lucidworks.cloud.OAuth2HttpRequestInterceptorBuilder;
import org.apache.commons.math3.distribution.ZipfDistribution;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.IOUtils;
import org.apache.solr.common.util.NamedList;

import java.io.*;
import java.util.*;

public class TSVRandomSolrQueryCreator {

    final static Random r = new Random();
    final static Set filterQueries = new HashSet();
    final static Set queries = new HashSet();

    static String[] words = new String[164773];
    static String[] colors = new String[865];
    static String[] brands = new String[397];
    static String[] categories = new String[21];
    static Object[] manufacturers = new String[1145];

    static ZipfDistribution wordsZipF = new ZipfDistribution(164772, 1);
    static ZipfDistribution colorsZipF = new ZipfDistribution(864, 0.7); // tweak distribution
    static ZipfDistribution brandsZipF = new ZipfDistribution(396, 0.85); // tweak distribution
    static ZipfDistribution categoriesZipF = new ZipfDistribution(20, 1);
    static ZipfDistribution manuZipF = new ZipfDistribution(1144, 1);

    static int limit = 10000;

    public static void main(String args[]) throws Exception {

        long num = Integer.parseInt(args[0]);
        String inputPath = args[1];
        String outputPath = args[2];
        String outputFileName = args[3];

        // required information to access the managed search service
        String customerId = "lucidworks";
        String solrUrl = "https://dev.cloud.lucidworks.com/lucidworks/example/solr";
        String oauth2ClientId = System.getProperty("OAUTH2_CLIENT_ID", "0oacqhlrhSu5CI589356");
        String oauth2ClientSecret = System.getProperty("OAUTH2_CLIENT_SECRET", "nvafkAULhxBBsRWPfJFkWGBUwRwmVBWYahzqjM3u");

        // create http request interceptor and start it
        OAuth2HttpRequestInterceptor oauth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(customerId, oauth2ClientId, oauth2ClientSecret).build();
        oauth2HttpRequestInterceptor.start();
        // register http request interceptor with solrj
        HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor);

        // create cloud solr client with the solr url
        CloudSolrClient client = new CloudSolrClient.Builder().withSolrUrl(solrUrl).build();
        String collection = "wiki";
        client.setDefaultCollection(collection);

        // words
        BufferedReader in = new BufferedReader(new FileReader(inputPath + "words.txt"));
        for (int w = 0; w < words.length; w++) {
            words[w] = in.readLine();
        }
        in.close();
        // colors
        in = new BufferedReader(new FileReader(inputPath + "colors.txt"));
        for (int w = 0; w < colors.length; w++) {
            colors[w] = in.readLine();
        }
        in.close();

        // brands
        in = new BufferedReader(new FileReader(inputPath + "brands.txt"));
        for (int w = 0; w < brands.length; w++) {
            brands[w] = in.readLine();
        }
        in.close();

        // categories
        in = new BufferedReader(new FileReader(inputPath + "category.txt"));
        for (int w = 0; w < categories.length; w++) {
            categories[w] = in.readLine();
        }
        in.close();

        // manu
        in = new BufferedReader(new FileReader(inputPath + "manufacturer.txt"));
        for (int w = 0; w < manufacturers.length; w++) {
            manufacturers[w] = in.readLine();
        }
        in.close();

        FileWriter fw = new FileWriter(outputPath + outputFileName, true);
        BufferedWriter bw = new BufferedWriter(fw);
        PrintWriter writer = new PrintWriter(bw);
        writer.println("id" + '\t' + "params");

        for (int x = 0; x < num; x++) {

            StringBuilder query = new StringBuilder("q=");
            // main query
            String mainQ = generateMixedQueries();
            query.append(mainQ);

            /// 50% 1 filter, 25% 2 filters,
            int filtersCount = 1;
            int filterRandomNo = r.nextInt(8);
            if (filterRandomNo >= 4 && filterRandomNo < 6) {
                filtersCount = 2;
            }
            if (filterRandomNo == 6) {
                filtersCount = 3;
            }
            if (filterRandomNo == 7) {
                filtersCount = 4;
            }

            List<String> filterFieldNames = getFilterFieldNames();
            int lowPerfQuery = r.nextInt(30);
            if (lowPerfQuery == 15) {
                for (int n = 0; n < filtersCount; n++) {
                    String filterFieldName = getFilterFieldName(filterFieldNames);
                    query.append("&fq=").append(filterFieldName);
                    query.append(":");
                    query.append(getFilterQueryValue(filterFieldName));
                    filterFieldNames.remove(filterFieldName);
                }
            }
            // make live query to index and fetch filter queries
            else {
                boolean redo = true;
                int retries = 0;
                while (redo) {
                    retries ++;
                    StringBuilder fqStr = new StringBuilder();
                    fqStr.trimToSize();
                    if (retries == 10) {
                        for (int n = 0; n < filtersCount; n++) {
                            filterFieldNames = getFilterFieldNames();
                            String filterFieldName = getFilterFieldName(filterFieldNames);
                            fqStr.append("&fq=").append(filterFieldName);
                            fqStr.append(":");
                            fqStr.append(getFilterQueryValue(filterFieldName));
                            filterFieldNames.remove(filterFieldName);
                        }
                        break;
                    }
                    redo = false;
                    ModifiableSolrParams params = new ModifiableSolrParams();
                    params.add("q", mainQ)
                            .add("rows", "1")
                            .add("fl", "id")
                            .add("facet", "true")
                            .add("facet.limit", String.valueOf(limit))
                            .add("qf", "title_t description_t features_t color_t category_t_mv " +
                                    "brand_t specifications_t manufacturer_t");

                    for (int n = 0; n < filtersCount; n++) {

                        String filterFieldName = getFilterFieldName(filterFieldNames);
                        if (filterFieldName.equals("price_f")) {
                            int start = r.nextInt(6) * 100;
                            int gap = (1 + r.nextInt(3)) * 100;
                            fqStr.append("&fq=").append(filterFieldName).append(":");
                            if (r.nextInt(5) == 4) {
                                if (r.nextBoolean()) {
                                    params.add("fq", filterFieldName + ":[" + start + " TO *]");
                                    fqStr.append("[").append(start).append(" TO *]");
                                } else {
                                    params.add("fq", filterFieldName + ":[0 TO " + (start + gap) + "]");
                                    fqStr.append("[").append("0").append(" TO ").append(start + gap).append("]");
                                }
                            } else {
                                params.add("fq", filterFieldName + ":[" + start + " TO " + (start + gap) + "]");
                                fqStr.append("[").append(start).append(" TO ").append(start + gap).append("]");
                            }
                            QueryResponse response = client.query(params);
                            if (response.getResults().isEmpty()) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }
                        } else if (filterFieldName.equals("shipping_f")) {
                            int start = r.nextInt(4);
                            int end = start + r.nextInt(4) + 1;
                            fqStr.append("&fq=").append(filterFieldName).append(":");
                            fqStr.append("[").append(start).append(" TO ").append(end).append("]");
                            params.add("fq", filterFieldName + ":[" + start + " TO " + end + "]");
                            QueryResponse response = client.query(params);
                            if (response.getResults().isEmpty()) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }
                        } else if (filterFieldName.equals("rating_f")) {
                            int start = r.nextInt(3);
                            int gap = 3;
                            fqStr.append("&fq=").append(filterFieldName).append(":");
                            if (r.nextInt(5) == 4) {
                                if (r.nextBoolean()) {
                                    params.add("fq", filterFieldName + ":[" + start + " TO 5]");
                                    fqStr.append("[").append(start).append(" TO ").append("5").append("]");
                                } else {
                                    params.add("fq", filterFieldName + ":[0 TO " + (start + gap) + "]");
                                    fqStr.append("[").append("0").append(" TO ").append(start + gap).append("]");
                                }
                            } else {
                                params.add("fq", filterFieldName + ":[" + start + " TO " + (start + gap) + "]");
                                fqStr.append("[").append(start).append(" TO ").append(start + gap).append("]");
                            }
                            QueryResponse response = client.query(params);
                            if (response.getResults().isEmpty()) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }
                        } else {
                            params.remove("facet.field");
                            params.add("facet.field", filterFieldName);

                            QueryResponse response = client.query(params);
                            NamedList responseList = response.getResponse();
                            if (response.getResults().isEmpty()) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }
                            NamedList facet_counts = (NamedList) (responseList.get("facet_counts"));
                            NamedList facet_fields = (NamedList) (facet_counts.get("facet_fields"));
                            NamedList facetFiledValues = (NamedList) (facet_fields.get(filterFieldName));
                            Map facetFiledMap = facetFiledValues.asShallowMap();
                            Object[] keySet = facetFiledValues.asShallowMap().keySet().toArray();
                            if (keySet.length == 0) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }

                            // 20% time add two filter values
                            int numFilterValsFlag = r.nextInt(5);
                            fqStr.append("&fq=").append(filterFieldName).append(":");

                            String fVal = keySet[r.nextInt(10)].toString();
                            int localRetries = 0;
                            while (fVal.trim().isEmpty() && facetFiledMap.get(fVal.trim()).toString().
                                    equals("0") && localRetries != 1000) {
                                localRetries++;
                                fVal = keySet[r.nextInt(10)].toString();
                            }
                            if (localRetries==1000) {
                                redo = true;
                                fqStr.delete(0,  fqStr.length());
                                break;
                            }
                            String localFilterParam = filterFieldName + ":" + "\"" + fVal + "\"";
                            // add filters
                            fqStr.append("\"" + fVal + "\"" );
                            if (numFilterValsFlag == 4) {
                                localRetries = 0;
                                fVal = keySet[r.nextInt(10)].toString();
                                while (fVal.trim().isEmpty() && facetFiledMap.get(fVal.trim()).toString().
                                        equals("0") && localRetries != 1000) {
                                    localRetries++;
                                    fVal = keySet[r.nextInt(10)].toString();
                                }
                                if (localRetries==1000) {
                                    redo = true;
                                    fqStr.delete(0,  fqStr.length());
                                    break;
                                }
                                fqStr.append(" ").append("\"" + fVal + "\"" );
                                localFilterParam = localFilterParam.concat(" ").concat("\"" + fVal + "\"" );
                            }
                            params.add("fq", localFilterParam);
                        }
                        filterFieldNames.remove(filterFieldName);
                        QueryResponse response = client.query(params);
                        if (response.getResults().isEmpty()) {
                            redo = true;
                            fqStr.delete(0,  fqStr.length());
                            break;
                        }
                    }
                    query.append(fqStr.toString());
                }
            }

            /// 70% 0-20, 20% 10-20, 10% 20-30 filters,
            int rowsCount = 1;
            int noOfRowsRNo = r.nextInt(10);
            if (noOfRowsRNo > 6 && noOfRowsRNo < 9) {
                rowsCount = 2;
            }
            if (noOfRowsRNo == 9) {
                rowsCount = 3;
            }

            int rows = 20;
            int start = 20 * (rowsCount - 1);
            query.append("&rows=").append(rows).append("&start=").append(start);

            query.append("&json.facet=");
            query.append("{" +
                    "  colors : {" +
                    "    type : terms," +
                    "    field : color_s," +
                    "    limit : 5" +
                    "  }," +
                    "  categories : {" +
                    "    type : terms," +
                    "    field : category_s," +
                    "    limit : 5" +
                    "  }," +
                    "  models : {" +
                    "    type : terms," +
                    "    field : model_s," +
                    "    limit : 5" +
                    "  }," +
                    "  manufacturers : {" +
                    "    type : terms," +
                    "    field : manufacturer_s," +
                    "    limit : 5" +
                    "  }," +
                    "  features : {" +
                    "    type : terms," +
                    "    field : features_s," +
                    "    limit : 5" +
                    "  }," +
                    "  prices : {" +
                    "    type : range," +
                    "    field : price_f," +
                    "    start : 0," +
                    "    end : 1200," +
                    "    gap : 100" +
                    "  }," +
                    "  shipping : {" +
                    "    type : range," +
                    "    field : shipping_f," +
                    "    start : 0," +
                    "    end : 1200," +
                    "    gap : 100" +
                    "  }," +
                    "  ratings : {" +
                    "    type : range," +
                    "    field : rating_f," +
                    "    start : 0," +
                    "    end : 5," +
                    "    gap : 1" +
                    "  },  " +
                    "  availability : {" +
                    "    type : terms," +
                    "    field : is_available_b" +
                    "  }" +
                    "}");

            // query fields with their boos factos
            query.append("&qf=title_t description_t features_t color_t category_t_mv brand_t specifications_t manufacturer_t");
            writer.println(String.valueOf(x) + '\t' + query.toString());
        }
        writer.close();

        // remove the interceptor from the request chain
        HttpClientUtil.removeRequestInterceptor(oauth2HttpRequestInterceptor);
        // close the http request interceptor to stop background token refresh
        IOUtils.closeQuietly(oauth2HttpRequestInterceptor);
    }

    private static String generateMixedQueries() {
        StringBuilder query = new StringBuilder("");
        // queries - 1 to 3
        boolean useZipF = r.nextBoolean();
        int mainQ = 1 + r.nextInt(3);
        while (mainQ > 0) {
            int index = r.nextInt(words.length);
            if (useZipF) {
                index = wordsZipF.sample();
            }
            if (r.nextBoolean()) {
                if (words[index].length() > 5) {
                    query.append(words[index]);
                    query.append(" ");
                    mainQ--;
                }
            } else {
                query.append(words[index]);
                query.append(" ");
                mainQ--;
            }
        }
        // add more query terms with other params
        // 66.67% queries
        if (r.nextInt(3) > 0) {
            int extras = r.nextInt(2);
            for (int i = 0; i < extras; i++) {
                int index = r.nextInt(4);
                switch (index) {
                    case 0:
                        query.append(colors[r.nextInt(colors.length)]);
                        break;
                    case 1:
                        query.append(brands[r.nextInt(brands.length)]);
                        break;
                    case 2:
                        query.append(categories[r.nextInt(categories.length)]);
                        break;
                    case 3:
                        query.append(manufacturers[r.nextInt(manufacturers.length)]);
                        break;
                }
                query.append(" ");
            }
        }
        return query.toString().trim();
    }

    private static String getFilterQueryValue(String fieldName) {
        StringBuilder query = new StringBuilder("");
        switch (fieldName) {
            case "features_s":
                int c = 1 + r.nextInt(5);
                query//.append("(")
                        .append(words[wordsZipF.sample()]);
                for (int i = 0; i < c; i++) {
                    query.append(" ");
                    query.append(words[wordsZipF.sample()]);
                }
                //query.append(")");
                break;
            case "color_s":
                query.append(colors[colorsZipF.sample()]);
                break;
            case "brand_s":
                query.append(brands[brandsZipF.sample()]);
                break;
            case "category_s":
                c = 1 + r.nextInt(2);
                query//.append.("(")
                        .append(categories[categoriesZipF.sample()]);
                for (int i = 0; i < c; i++) {
                    query.append(" OR ");
                    query.append(categories[categoriesZipF.sample()]);
                }
                //query.append(")");
                break;
            case "model_s":
                query.append(r.nextBoolean() ? "M" : "N").append(r.nextInt(10)).append(
                        r.nextInt(10)).append(r.nextInt(10));
                break;
            case "price_f":
                int start = r.nextInt(6) * 100;
                int gap = (1 + r.nextInt(3)) * 100;
                if (r.nextInt(5) == 4) {
                    if (r.nextBoolean()) {
                        query.append("[").append(start).append(" TO ").append("*").append("]");
                    } else {
                        query.append("[").append("0").append(" TO ").append(start + gap).append("]");
                    }
                } else {
                    query.append("[").append(start).append(" TO ").append(start + gap).append("]");
                }
                break;
            case "shipping_f":
                start = r.nextInt(4);
                int end = start + r.nextInt(4) + 1;
                query.append("[").append(start).append(" TO ").append(end).append("]");
                break;
            case "rating_f":
                start = r.nextInt(3);
                gap = 3;
                if (r.nextInt(5) == 4) {
                    if (r.nextBoolean()) {
                        query.append("[").append(start).append(" TO ").append("5").append("]");
                    } else {
                        query.append("[").append("0").append(" TO ").append(start + gap).append("]");
                    }
                } else {
                    query.append("[").append(start).append(" TO ").append(start + gap).append("]");
                }
                break;
        }
        return query.toString();
    }

    private static List<String> getFilterFieldNames() {
        List<String> list = new ArrayList<>();
        list.add("features_s");
        list.add("color_s");
        list.add("brand_s");
        list.add("model_s");
        list.add("price_f");
        list.add("shipping_f");
        list.add("rating_f");
        return list;
    }

    private static String getFilterFieldName(List<String> filterFieldNames) {
        if (filterFieldNames.size() == 0) {
            filterFieldNames = getFilterFieldNames();
        }
        String name = filterFieldNames.get(r.nextInt(filterFieldNames.size()));
        if (name.endsWith("_f")) {
            name = filterFieldNames.get(r.nextInt(filterFieldNames.size()));
            if (name.endsWith("_f")) {
                name = filterFieldNames.get(r.nextInt(filterFieldNames.size()));
            }
        }
        return name;
    }
}
