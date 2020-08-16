import java.io.{File, FileReader}
import java.lang.invoke.MethodHandles
import java.net.URL
import java.util.{Properties, Scanner}

import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import lucidworks.gatling.solr.Predef._
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.control.Breaks.break

class RampUpQuerySimulation extends Simulation {

  private val log = LoggerFactory.getLogger(MethodHandles.lookup.lookupClass)

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/query.config.properties")
    prop.load(propFile)

    val queryFeederSource = prop.getProperty("queryFeederSource", "/opt/gatling/user-files/" +
      "data/solrQueries.tsv")
    val numQueriesPerUser = prop.getProperty("numQueriesPerUser", "5")
    val queryMaxNumUsers = prop.getProperty("queryMaxNumUsers", "3")
    val queryMinNumUsers = prop.getProperty("queryMinNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "1")
    val basequery = prop.getProperty("basequery", "${params}&defType=edismax&qf=title description")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr/")
    val apiKey = prop.getProperty("apiKey", "--empty-here--")
    val defaultCollection = prop.getProperty("defaultCollection", "test")
    val numClients = prop.getProperty("numClients", "1")
    val oauth2CustomerId = prop.getProperty("CUSTOMER_ID", "lucidworks")

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv.txt")
    val indexUrlPath = prop.getProperty("indexUrlPath", null)
    val numBatchesPerUser = prop.getProperty("numBatchesPerUser", "1")
    val indexMaxNumUsers = prop.getProperty("indexMaxNumUsers", "1")
    val indexMinNumUsers = prop.getProperty("indexMinNumUsers", "1")
    val indexBatchSize = prop.getProperty("indexBatchSize", "5000")
    val indexParallelNodes = prop.getProperty("indexParallelNodes", "1")
    val indexTotalFiles = prop.getProperty("indexTotalFiles", "1")

    val updateFilePath = prop.getProperty("updateFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv.txt")
    val updateUrlPath = prop.getProperty("updateUrlPath", null)
    val updateNumBatchesPerUser = prop.getProperty("updateNumBatchesPerUser", "1")
    val updateMaxNumUsers = prop.getProperty("updateMaxNumUsers", "1")
    val updateMinNumUsers = prop.getProperty("updateMinNumUsers", "1")
    val updateBatchSize = prop.getProperty("updateBatchSize", "5000")
    val updateParallelNodes = prop.getProperty("updateParallelNodes", "1")
    val updateTotalFiles = prop.getProperty("updateTotalFiles", "1")

    val header = prop.getProperty("header", "id,title,time,description")
    val headerSep = prop.getProperty("header.sep", ",")
    val multiParamSep = prop.getProperty("multiParam.sep", null)
    val fieldValuesSep = prop.getProperty("fieldValues.sep", ",")

    val podNo = if (System.getenv("POD_NAME") != null) {
      System.getenv("POD_NAME")
    }.split("-")(1)
    else {
      "gatlingsolr-1"
    }.split("-")(1)

  }

  object Query {
    // construct a feeder for our query params stored in the csv
    val feeder = tsv(Config.queryFeederSource).circular

    // each user sends loops queries
    val search = repeat(Config.numQueriesPerUser) {
      feed(feeder).exec(solr("queryRequest")
        // query to do
        .query[String](Config.basequery))
    }
  }

  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  val solrConf = solr.zkhost(Config.zkHost).collection(Config.defaultCollection).numClients(Config.numClients.toInt).properties(Config.prop)

  // A scenario where users execute queries
  val query = scenario("QUERY").exec(Query.search)

    setUp(
      query.inject(
        rampUsersPerSec(Config.queryMinNumUsers.toDouble) to Config.queryMaxNumUsers.toDouble during
          (Config.totalTimeInMinutes.toDouble minutes)).protocols(solrConf)
    )

}