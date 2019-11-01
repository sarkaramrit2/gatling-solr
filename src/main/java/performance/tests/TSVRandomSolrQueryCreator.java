package performance.tests;

import org.apache.commons.math3.distribution.ZipfDistribution;

import java.io.*;
import java.util.*;

public class TSVRandomSolrQueryCreator {

    final static Random r = new Random();
    final static Set filterQueries = new HashSet();
    final static Set queries = new HashSet();

    static String[] words = new String[3001];
    static String[] colors = new String[865];
    static String[] brands = new String[397];
    static String[] categories = new String[21];
    static Object[] manufacturers = new String[1145];

    static ZipfDistribution wordsZipF = new ZipfDistribution(3000, 1);
    static ZipfDistribution colorsZipF = new ZipfDistribution(864, 0.7); // tweak distribution
    static ZipfDistribution brandsZipF = new ZipfDistribution(396, 0.85); // tweak distribution
    static ZipfDistribution categoriesZipF = new ZipfDistribution(20, 1);
    static ZipfDistribution manuZipF = new ZipfDistribution(1144, 1);

    public static void main(String args[]) throws Exception {

        long num = Integer.parseInt(args[0]);
        String inputPath = args[1];
        String outputPath = args[2];
        String outputFileName = args[3];

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
            query.append(generateMixedQueries());

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

            List<String> filterFieldNames = new ArrayList<>();
            filterFieldNames.add("color_s");
            filterFieldNames.add("brand_s");
            filterFieldNames.add("category_s");
            filterFieldNames.add("models_s");
            filterFieldNames.add("price_f");
            filterFieldNames.add("shipping_f");

            for (int n = 0; n < filtersCount; n++) {
                String filterFieldName = filterFieldNames.get(r.nextInt(filterFieldNames.size() - 1));
                query.append("&fq=").append(filterFieldName);
                query.append(":");
                query.append(getFilterQueryValue(filterFieldName));
                filterFieldNames.remove(filterFieldName);
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
            query.append("{" + "  colors : {" + "    type : terms," + "    field : color_s," + "    limit : 5" + "  }," + "  categories : {" + "    type : terms," + "    field : category_s," + "    limit : 5" + "  }," + "  models : {" + "    type : terms," + "    field : model_s," + "    limit : 5" + "  }," + "  manufacturers : {" + "    type : terms," + "    field : manufacturer_s," + "    limit : 5" + "  }," + "  prices : {" + "    type : range," + "    field : price_f," + "    start : 0," + "    end : 1200," + "    gap : 100" + "  }," + "  shipping : {" + "    type : range," + "    field : shipping_f," + "    start : 0," + "    end : 1200," + "    gap : 100" + "  }," + "  availability : {" + "    type : terms," + "    field : isAvailable" + "  }" + "}");

            writer.println(String.valueOf(x) + '\t' + query.toString());
        }
        writer.close();
    }

    private static String generateMixedQueries() {
        StringBuilder query = new StringBuilder("");
        // queries - 1 to 3
        int mainQ = 1 + r.nextInt(3);
        while (mainQ > 0) {
            int index = r.nextInt(words.length);
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
        if (r.nextBoolean()) {
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
            case "color_s":
                query.append(colors[colorsZipF.sample()]);
                break;
            case "brand_s":
                query.append(brands[brandsZipF.sample()]);
                break;
            case "category_s":
                int c = r.nextInt(2);
                query.append("(").append(categories[categoriesZipF.sample()]);
                for (int i = 0; i < c; i++) {
                    query.append(" AND ");
                    query.append(categories[categoriesZipF.sample()]);
                }
                query.append(")");
                break;
            case "models_s":
                query.append(r.nextBoolean() ? "M" : "N").append(r.nextInt(10)).append(
                        r.nextInt(10)).append(r.nextInt(10));
                break;
            case "price_f":
                int start = r.nextInt(6) * 100;
                int gap = (1 + r.nextInt(2)) * 100;
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
                query.append("[").append("*").append(" TO ").append(2 * r.nextInt(3)).append("]");
                break;
        }
        return query.toString();
    }
}
