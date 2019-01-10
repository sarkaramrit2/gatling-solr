import com.opencsv.CSVReader;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class WikiIndexer {
    private static final String CSV_FILE_PATH
            = "/Users/apple/enwiki.random.lines.tsv";

    final static String zkHost1 = "localhost:9983";
    final static CloudSolrClient client1 = new CloudSolrClient.Builder().withZkHost(zkHost1).build();
    final static String collection1 = "wiki";
    static UpdateRequest updateRequest = new UpdateRequest();

    public static void main(String[] args) {
        readDataLineByLine(CSV_FILE_PATH);
    }

    public static void readDataLineByLine(String file) {
        List<SolrInputDocument> docs = new ArrayList<>();
        try {

            // Create an object of filereader class
            // with CSV file as a parameter.
            FileReader filereader = new FileReader(file);

            for (int x = 0; x < 5; x++) {

                // create csvReader object passing
                // filereader as parameter
                CSVReader csvReader = new CSVReader(filereader, '\t');
                String[] nextRecord;

                // we are going to read data line by line
                long z = 0;
                while ((nextRecord = csvReader.readNext()) != null) {
                    SolrInputDocument document = new SolrInputDocument();
                    document.addField("id", (5000000 * x) + ++z);
                    if (nextRecord[0].length() > 32000) {
                        document.addField("description", nextRecord[0]);
                        continue;
                    } else {
                        document.addField("title", nextRecord[0]);
                    }
                    if (nextRecord.length > 1 && nextRecord[1].length() <= 30000) {
                        document.addField("time", nextRecord[1]);
                    }
                    if (nextRecord.length > 2) {
                        document.addField("description", nextRecord[2]);
                    }
                    docs.add(document);
                    if (docs.size() % 5000 == 0) {
                        updateRequest.add(docs);
                        client1.request(updateRequest, collection1);
                        docs.clear();
                        System.out.println((5000000 * x) + ++z);
                    }
                    if (docs.size() % 5000 == 0) {
                        updateRequest.commit(client1, collection1);
                    }
                }
                updateRequest.add(docs);
                client1.request(updateRequest, collection1);
                docs.clear();
                updateRequest.commit(client1, collection1);
            }
        } catch (Exception e) {
            try {
                updateRequest.add(docs);
                client1.request(updateRequest, collection1);
                docs.clear();
                updateRequest.commit(client1, collection1);

            } catch (Exception ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }
}