import java.util.Properties

import lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import org.apache.solr.client.solrj.impl.CloudSolrClient

import scala.concurrent.duration._

class QuerySimulation extends Simulation {

  object Config {
    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/query.config.properties")
    prop.load(propFile)

    val queryFeederSource = prop.getProperty("queryFeederSource", "/opt/gatling/user-files/" +
      "data/solrQueries.tsv")
    val numQueriesPerUser = prop.getProperty("numQueriesPerUser", "5")
    val maxNumUsers = prop.getProperty("maxNumUsers", "3")
    val minNumUsers = prop.getProperty("minNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "1")
    val basequery = prop.getProperty("basequery", "${params}&defType=edismax&qf=title description")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr")
    val apiKey = prop.getProperty("apiKey", "--empty-here--")
    val defaultCollection = prop.getProperty("defaultCollection", "wiki")
    val numClients = prop.getProperty("numClients", "1")
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

  val client = new CloudSolrClient.Builder().withZkHost(Config.zkHost).build()
  client.setDefaultCollection(Config.defaultCollection)
  client.commit(false, true)

  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  val solrConf = solr.zkhost(Config.zkHost).collection(Config.defaultCollection)
    .numClients(Config.numClients.toInt).properties(Config.prop)

  // A scenario where users execute queries
  val users = scenario("Users").exec(Query.search)

  setUp(
    users.inject(
      constantUsersPerSec(Config.maxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))//,
      //rampUsersPerSec(Config.minNumUsers.toDouble) to Config.maxNumUsers.toDouble during
        //(Config.totalTimeInMinutes.toDouble minutes))
  ).protocols(solrConf)
}