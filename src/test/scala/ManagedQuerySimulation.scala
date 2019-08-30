import java.util.Properties

import com.lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import org.apache.solr.client.solrj.impl.CloudSolrClient

import scala.concurrent.duration._
import com.lucidworks.cloud.OAuth2HttpRequestInterceptor
import com.lucidworks.cloud.OAuth2HttpRequestInterceptorBuilder
import org.apache.solr.client.solrj.impl.HttpClientUtil
// required information to access the managed search service// required information to access the managed search service

class ManagedQuerySimulation extends Simulation {

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
        .managedQuery[String](Config.basequery))
    }
  }

  val oauth2ClientId: String = System.getProperty("OAUTH2_CLIENT_ID")
  val oauth2ClientSecret: String = System.getProperty("OAUTH2_CLIENT_SECRET")

  // create http request interceptor and start it
  val oauth2HttpRequestInterceptor: OAuth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(oauth2ClientId, oauth2ClientSecret).build
  oauth2HttpRequestInterceptor.start()

  // register http request interceptor with solrj
  HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor)

  val client = new CloudSolrClient.Builder().withZkHost(Config.zkHost).build()
  client.setDefaultCollection(Config.defaultCollection)
  client.commit(false, true)

  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  val solrConf = solr.solrurl(Config.solrUrl).collection(Config.defaultCollection)
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