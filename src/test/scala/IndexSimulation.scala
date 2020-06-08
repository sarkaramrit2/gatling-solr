import java.io.{BufferedReader, File, FileReader}
import java.util
import java.util.Properties

import lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.apache.solr.common.SolrInputDocument


class IndexSimulation extends Simulation {

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/index.config.properties")
    prop.load(propFile)

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki-20120502-lines-1k.txt")
    val numBatchesPerUser = prop.getProperty("numBatchesPerUser", "999999")
    val maxNumUsers = prop.getProperty("maxNumUsers", "9")
    val minNumUsers = prop.getProperty("minNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "1")
    val indexBatchSize = prop.getProperty("indexBatchSize", "100")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr")
    val apiKey = prop.getProperty("apiKey", "--empty-here--")
    val defaultCollection = prop.getProperty("defaultCollection", "wiki")
    val header = prop.getProperty("header", "title,time,description")
    val headerSep = prop.getProperty("header.sep", ",")
    val multiParamSep = prop.getProperty("multiParam.sep", "\t")
    val fieldValuesSep = prop.getProperty("fieldValues.sep", ",")
    val numClients = prop.getProperty("numClients", "9")

  }

  val solrIndexV2Feeder = new Feeder[util.ArrayList[SolrInputDocument]] {

    private val indexFile = new File(Config.indexFilePath)
    private val fileReader = new FileReader(indexFile)
    private val reader = new BufferedReader(fileReader)

    private var hasNextLine = ""

    override def hasNext = hasNextLine != null

    override def next: Map[String, util.ArrayList[SolrInputDocument]] = {
      var batchSize = Config.indexBatchSize.toInt
      val records = new util.ArrayList[SolrInputDocument]()
      var record = reader.readLine()
      while (batchSize > 0 && record != null) {
        val doc = new SolrInputDocument()
        val fieldNames = Config.header.split(Config.headerSep) // default comma
        val fieldValues = record.split(Config.fieldValuesSep) // default comma

        for (i <- 0 until fieldNames.length) {
          if (fieldValues.length - 1 >= i) {
            val multiValues = fieldValues(i).trim.split(Config.multiParamSep);
            for (i <- 0 until multiValues.length) {
              doc.addField(fieldNames(i), multiValues(i).trim);
            }
          }
        }
        records.add(doc)
        batchSize = batchSize - 1
        record = reader.readLine()
      }

      hasNextLine = record

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

}