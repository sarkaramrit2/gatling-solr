import java.util.concurrent.TimeUnit
import java.util.{Collections, Properties}

import com.lucidworks.cloud.{ManagedSearchClusterStateProvider, OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import io.gatling.core.Predef._
import lucidworks.gatling.solr.Predef._
import org.apache.solr.client.solrj.impl.{CloudSolrClient, HttpClientUtil}

import scala.concurrent.duration._
// required information to access the managed search service// required information to access the managed search service

class ManagedConstantLogQuerySimulation extends Simulation {

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
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr/")
    val apiKey = prop.getProperty("apiKey", "--empty-here--")
    val defaultCollection = prop.getProperty("defaultCollection", "test")
    val numClients = prop.getProperty("numClients", "1")
    val oauth2CustomerId = prop.getProperty("CUSTOMER_ID", "lucidworks")

  }

  object Query {
    // construct a feeder for our query params stored in the csv
    val feeder = tsv(Config.queryFeederSource).circular

    // each user sends loops queries
    val search = repeat(Config.numQueriesPerUser) {
      feed(feeder).exec(solr("queryRequest")
        // query to do
        .managedLogQuery[String](Config.basequery))
    }
  }

  val clientId = Option(System.getenv("OAUTH2_CLIENT_ID"))
  val oauth2ClientId: String = if (clientId.isDefined) clientId.get else System.getProperty("OAUTH2_CLIENT_ID")
  val clientSecret = Option(System.getenv("OAUTH2_CLIENT_SECRET"))
  val oauth2ClientSecret: String = if (clientSecret.isDefined) clientSecret.get else System.getProperty("OAUTH2_CLIENT_SECRET")

  // create http request interceptor and start it
  val oauth2HttpRequestInterceptor: OAuth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(Config.oauth2CustomerId, oauth2ClientId, oauth2ClientSecret).build
  oauth2HttpRequestInterceptor.start()
  oauth2HttpRequestInterceptor.awaitFirstRefresh(60, TimeUnit.SECONDS);

  // register http request interceptor with solrj
  HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor)

  val client = new CloudSolrClient.Builder(new ManagedSearchClusterStateProvider(Collections.singletonList(Config.solrUrl))).build()
  client.setDefaultCollection(Config.defaultCollection)
  client.commit(false, true)

  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  val solrConf = solr.solrurl(Config.solrUrl).collection(Config.defaultCollection)
    .numClients(Config.numClients.toInt).properties(Config.prop)

  // A scenario where users execute queries
  val users = scenario("Users").exec(Query.search)

  setUp(
    users.inject(
      constantUsersPerSec(Config.maxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
  ).protocols(solrConf)
}