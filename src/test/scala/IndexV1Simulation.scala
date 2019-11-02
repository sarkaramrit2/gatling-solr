import java.io.{File, FileReader}
import java.util.{Properties, Scanner}

import lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.apache.solr.client.solrj.impl.CloudSolrClient
import scala.util.control.Breaks._

class IndexV1Simulation extends Simulation {

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/index.1.config.properties")
    prop.load(propFile)

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv")
    val numBatchesPerUser = prop.getProperty("numBatchesPerUser", "200")
    val maxNumUsers = prop.getProperty("maxNumUsers", "2")
    val minNumUsers = prop.getProperty("minNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "1")
    val indexBatchSize = prop.getProperty("indexBatchSize", "5000")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr")
    val apiKey = prop.getProperty("apiKey", "--empty-here--")
    val defaultCollection = prop.getProperty("defaultCollection", "wiki")
    val header = prop.getProperty("header", "title,time,description")
    val numClients = prop.getProperty("numClients", "1")
    val parallelNodes = prop.getProperty("parallelNodes", "1")
    val totalFiles = prop.getProperty("totalFiles", "1")
    val podNo = if (System.getenv("POD_NAME") != null) {
      System.getenv("POD_NAME")
      }.split("-")(1)
    else {
      "gatlingsolr-0"
      }.split("-")(1)
  }

  val solrIndexV1Feeder = new Feeder[String] {

    private var podNo = Config.podNo.toInt
    private var indexFile: File = _
    if (Config.totalFiles.toInt <= 1) {
      System.out.println("indexFile: " + Config.indexFilePath)
      indexFile = new File(Config.indexFilePath)
    }
    else {
      System.out.println("indexFile: " + Config.indexFilePath + Config.podNo)
      indexFile = new File(Config.indexFilePath + Config.podNo)
    }

    private var fileReader = new FileReader(indexFile)
    private var scanner = new Scanner(fileReader)

    override def hasNext = if (!scanner.hasNext) {
      if (Config.totalFiles.toInt <= 1) {
        false
      }
      else {
        if (podNo + Config.parallelNodes.toInt > Config.totalFiles.toInt) {
          false
        }
        else {
          podNo = podNo + Config.parallelNodes.toInt
          indexFile = new File(Config.indexFilePath + Config.podNo)
          fileReader = new FileReader(indexFile)
          scanner = new Scanner(fileReader)
          true
        }
      }
    }
    else {
      true
    }

    override def next: Map[String, String] = {
      var batchSize = Config.indexBatchSize.toInt
      var record = ""
      while (batchSize > 0) {
        if (scanner.hasNext()) {
          record += scanner.nextLine()
          batchSize = batchSize - 1
          if (batchSize > 0) {
            record += '\n'
          }
        }
        else {
          break
        };
      }

      Map(
        "record" -> record)
    }
  }

  object Index {
    // construct a feeder for content stored in CSV file
    val feeder = solrIndexV1Feeder

    // each user sends batches
    val search = repeat(Config.numBatchesPerUser) {
      feed(feeder).exec(solr("indexV1Request")
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

}