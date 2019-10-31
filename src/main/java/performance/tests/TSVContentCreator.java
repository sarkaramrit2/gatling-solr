package performance.tests;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.distribution.ZipfDistribution;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class TSVContentCreator {

    private static final String SEP = "\t";
    private static final String MULTI_SEP = "#";
    private static final Random rand = new Random();
    private static final String[] dates = {"2012-01-20T17:33:18Z",
            "2012-02-20T17:33:18Z",
            "2012-03-20T17:33:18Z",
            "2012-04-20T17:33:18Z",
            "2012-05-20T17:33:18Z",
            "2012-06-20T17:33:18Z",
            "2012-07-20T17:33:18Z",
            "2012-08-20T17:33:18Z",
            "2012-09-20T17:33:18Z",
            "2012-10-20T17:33:18Z",
            "2012-11-20T17:33:18Z",
            "2012-12-20T17:33:18Z",
            "2013-01-20T17:33:18Z",
            "2013-02-20T17:33:18Z",
            "2013-03-20T17:33:18Z",
            "2013-04-20T17:33:18Z",
            "2013-05-20T17:33:18Z",
            "2013-06-20T17:33:18Z",
            "2013-07-20T17:33:18Z",
            "2013-08-20T17:33:18Z",
            "2013-09-20T17:33:18Z",
            "2013-10-20T17:33:18Z",
    };

    public static void main(String args[]) throws Exception {
        long num = Integer.parseInt(args[0]);
        String inputPath = args[1];
        String outputPath = args[2];
        String outputFileName = args[3];

        FileWriter fileWriter = new FileWriter(outputPath + outputFileName);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println("id" + SEP + "title_t" + SEP + "description_t" + SEP + "color_t" + SEP +
                "category_t_mv" + SEP + "brand_t" + SEP + "specifications_t" + SEP + "manufacturer_t" +
                SEP + "model_s" + SEP + "url_s" + SEP + "image_s" + SEP + "price_f" + SEP + "shipping_f" +
                SEP + "added_dt" + SEP + "loc_rpt" + SEP + "is_available_b");

        // words
        BufferedReader in = new BufferedReader(new FileReader(inputPath + "words.txt"));
        String[] words = new String[3001];
        for (int w = 0; w < words.length; w++) {
            words[w] = in.readLine();
        }
        in.close();
        // colors
        in = new BufferedReader(new FileReader(inputPath + "colors.txt"));
        String[] colors = new String[865];
        for (int w = 0; w < colors.length; w++) {
            colors[w] = in.readLine();
        }
        in.close();

        // brands
        in = new BufferedReader(new FileReader(inputPath + "brands.txt"));
        String[] brands = new String[397];
        for (int w = 0; w < brands.length; w++) {
            brands[w] = in.readLine();
        }
        in.close();

        // categories
        in = new BufferedReader(new FileReader(inputPath + "category.txt"));
        String[] categories = new String[21];
        for (int w = 0; w < categories.length; w++) {
            categories[w] = in.readLine();
        }
        in.close();

        // manu
        in = new BufferedReader(new FileReader(inputPath + "manufacturer.txt"));
        String[] manufacturers = new String[1145];
        for (int w = 0; w < manufacturers.length; w++) {
            manufacturers[w] = in.readLine();
        }
        in.close();

        ZipfDistribution abstractZipF = new ZipfDistribution(3000, 1);
        ZipfDistribution colorsZipF = new ZipfDistribution(864, 0.7); // tweak distribution
        ZipfDistribution brandsZipF = new ZipfDistribution(396, 0.85); // tweak distribution
        ZipfDistribution categoriesZipF = new ZipfDistribution(20, 1);
        ZipfDistribution manuZipF = new ZipfDistribution(1144, 1);

        for (long id = 0; id < num; id++) {
            String output = "";

            // description maker
            StringBuilder description = new StringBuilder();
            Set<String> descList = new HashSet<>();
            for (int w = 0; w < 70; w++) {
                int word = abstractZipF.sample();
                if (word > 60) {
                    description.append(words[word]);
                    descList.add(words[word]);
                    description.append(" ");
                }
            }

            // specs maker
            Set<String> specList = new HashSet<>();
            int totalSpec = 3 + rand.nextInt(4);
            while (totalSpec > 0) {
                int word = abstractZipF.sample();
                if (words[word].length() > 6) {
                    specList.add(words[word]);
                    totalSpec--;
                }
            }

            //title maker
            ZipfDistribution titleZipF = new ZipfDistribution(descList.size() - 1, 1);
            int total_t_no = 2 + rand.nextInt(3);
            Object[] descArray = descList.toArray();
            Set<String> titleSet = new HashSet<>();
            while (total_t_no > 0) {
                int word = titleZipF.sample();
                if (descArray[word].toString().length() > 5) {
                    titleSet.add(descArray[word].toString());
                    total_t_no--;
                    if (total_t_no == 0) {
                        if (titleSet.size() == 1) {
                            total_t_no++;
                        }
                    }
                }
            }
            String title = String.join(" ", titleSet);

            // category maker
            Set<String> categoriesList = new HashSet<>();
            for (int x = 0; x < 1 + rand.nextInt(3); x++) {
                categoriesList.add(categories[categoriesZipF.sample()].toString());
            }
            for (int x = 0; x < 1 + rand.nextInt(2); x++) {
                categoriesList.add(categories[rand.nextInt(categories.length - 1)].toString());
            }

            // location maker
            NormalDistribution componentA = new NormalDistribution(0, 10);
            NormalDistribution componentB = new NormalDistribution(0, 10);
            double lat = 40.7128;
            double lon = -74.0060;
            double moveLat = lat + componentA.sample();
            double moveLong = lon + componentB.sample();

            // price and shipping maker
            float price = Float.valueOf(String.valueOf(rand.nextInt(1200) + rand.nextGaussian()));
            float shipping = Float.valueOf(String.valueOf(rand.nextInt(10) + rand.nextGaussian()));

            output = String.valueOf(id); // id
            output = appendKeyValue(output, title); // title_t
            output = appendKeyValue(output, description.toString()); // description_t
            output = appendKeyValue(output, colors[colorsZipF.sample()]); // colors_s
            output = appendKeyValue(output, String.join(MULTI_SEP, categoriesList)); // category_t_mv
            int brandIndex = brandsZipF.sample();
            output = appendKeyValue(output, brands[brandIndex]); // brand_s
            output = appendKeyValue(output, String.join(" ", specList)); // specifications_t
            output = appendKeyValue(output, manufacturers[manuZipF.sample()]); // manufacturer_t

            String model = (rand.nextBoolean() ? "M" : "N") + rand.nextInt(10) +
                    rand.nextInt(10) + rand.nextInt(10);
            output = appendKeyValue(output, model); // model_s
            output = appendKeyValue(output, "http:///www.random.site.com/" + model + "-" + id +
                    title.toLowerCase().replace(' ', '-') + ".html"); // url_s

            output = appendKeyValue(output, "www.random.pictures.com/IMG-" + model + "-"
                    + id + "-" + rand.nextInt(1000) + ".jpeg"); // image_s
            output = appendKeyValue(output, Float.toString(price)); // price_f
            output = appendKeyValue(output, Float.toString(shipping)); // shipping_f

            // added maker
            int dayIndex = rand.nextInt(21);
            UniformIntegerDistribution hour = new UniformIntegerDistribution(1, 17);
            UniformIntegerDistribution minute = new UniformIntegerDistribution(1, 56);
            UniformIntegerDistribution second = new UniformIntegerDistribution(1, 56);
            String ho = Integer.toString(hour.sample());
            String mi = Integer.toString(minute.sample());
            String se = Integer.toString(second.sample());
            output = appendKeyValue(output, dates[dayIndex].replace("17", ho).replace("33", mi).replace("18", se)); // added_dt

            output = appendKeyValue(output, moveLat + "," + moveLong); // loc_rpt
            output = appendKeyValue(output, rand.nextInt(16) == 15); // is_available_b (6.25% not available)

            System.out.println(output);
            printWriter.println(output);

        }
        printWriter.close();
        fileWriter.close();
    }

    private static String appendKeyValue(String string, Object input, String sep) {
        return string.concat(sep).concat(input.toString());
    }

    private static String appendKeyValue(String string, Object input) {
        return appendKeyValue(string, input, SEP);
    }
}
