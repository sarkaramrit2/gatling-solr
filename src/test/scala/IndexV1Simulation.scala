import java.io.{File, FileReader}
import java.util.{Properties, Scanner}

import com.lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.apache.solr.client.solrj.impl.CloudSolrClient

class IndexV1Simulation extends Simulation {

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
    val numClients = prop.getProperty("numClients", "1")

  }

  val solrIndexFeeder = new Feeder[String] {

    val indexFile = new File(Config.indexFilePath)
    var fileReader = new FileReader(indexFile)
    var scanner = new Scanner(fileReader)

    override def hasNext = scanner.hasNext()

    override def next: Map[String, String] = {
      var batchSize = Config.indexBatchSize.toInt
      var record = ""
      while (batchSize > 0) {
        record += scanner.nextLine()
        batchSize = batchSize - 1
        if (batchSize > 0) {
          record += '\n'
        }
      }

      Map(
        "record" -> record)
    }
  }

  object Index {
    // construct a feeder for content stored in CSV file
    val feeder = solrIndexFeeder

    // each user sends batches
    val search = repeat(Config.numBatchesPerUser) {
      feed(feeder).exec(solr("indexRequest")
        .index(Config.header, "${record}")) // provide appropriate header
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
  client.commit(false, true)
}