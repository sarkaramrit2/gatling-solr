import java.io.{File, FileReader}
import java.util
import java.util.{Properties, Scanner}

import com.lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.common.SolrInputDocument

class IndexSimulation extends Simulation {

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/index.config.properties")
    prop.load(propFile)

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv")
    val numBatchesPerUser = prop.getProperty("numBatchesPerUser", "200")
    val maxNumUsers = prop.getProperty("maxNumUsers", "2")
    val minNumUsers = prop.getProperty("minNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "1")
    val indexBatchSize = prop.getProperty("indexBatchSize", "5000")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val defaultCollection = prop.getProperty("defaultCollection", "wiki")
    val header = prop.getProperty("header", "title,time,description")
    val headerSep = prop.getProperty("header.sep", ",")
    val fieldValuesSep = prop.getProperty("fieldValues.sep", ",")
    val numClients = prop.getProperty("numClients", "1")

  }

  val solrIndexV2Feeder = new Feeder[util.ArrayList[SolrInputDocument]] {

    private val indexFile = new File(Config.indexFilePath)
    private val fileReader = new FileReader(indexFile)
    private val scanner = new Scanner(fileReader)

    override def hasNext = scanner.hasNext()

    override def next: Map[String, util.ArrayList[SolrInputDocument]] = {
      var batchSize = Config.indexBatchSize.toInt
      val records = new util.ArrayList[SolrInputDocument]()
      while (batchSize > 0 && scanner.hasNext()) {
        val record = scanner.nextLine()
        val doc = new SolrInputDocument()
        val fieldNames = Config.header.split(Config.headerSep) // default comma
        val fieldValues = record.split(Config.fieldValuesSep) // default comma

        for (i <- 0 until fieldNames.length) {
          if (fieldValues.length - 1 >= i) {
            doc.addField(fieldNames(i), fieldValues(i).trim);
          }
        }
        records.add(doc)
        batchSize = batchSize - 1
      }

      Map(
        "record" -> records)
    }
  }

  object Index {
    // construct a feeder for content stored in CSV file
    val feeder = solrIndexV2Feeder

    // each user sends batches
    val search = repeat(Config.numBatchesPerUser) {
      feed(feeder).exec(solr("indexRequest")
        .indexV2(Config.header, feeder.next.get("record").get)) // provide appropriate header
    }
  }

  // pass zookeeper string, default collection to index, poolSize for CloudSolrClients
  val solrConf = solr.zkhost(Config.zkHost).collection(Config.defaultCollection)
    .numClients(Config.numClients.toInt).properties(Config.prop)

  // A scenario where users execute queries
  val users = scenario("Users").exec(Index.search)

  setUp(
    users.inject(
      atOnceUsers(Config.maxNumUsers.toInt))
  ).protocols(solrConf)

  val client = new CloudSolrClient.Builder().withZkHost(Config.zkHost).build()
  client.setDefaultCollection(Config.defaultCollection)
  client.commit()
}