import java.util.concurrent.{Executors, ThreadFactory, TimeUnit}
import java.util.{Properties}

import com.fasterxml.jackson.databind.ObjectMapper
import com.lucidworks.cloud.{OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import lucidworks.gatling.solr.Predef._
import lucidworks.gatling.solr.protocol.SolrProtocol
import org.apache.solr.client.solrj.impl.HttpClientUtil
import scalaj.http.Http

import scala.concurrent.duration._
// required information to access the managed search service// required information to access the managed search service

class ManagedRampUpHttpQuerySimulation extends Simulation {

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

    val jsonObjectMapper = new ObjectMapper()
    var jwtToken = ""
    var jwtExpiresIn : Long = 1790L

    def updateJwtToken() = {
      val loginUrl = s"https://pg01.us-west1.cloud.lucidworks.com/oauth2/default/" + Config.oauth2CustomerId + "/v1/token"
      val jsonResp = Http(loginUrl).postData("grant_type=client_credentials&scope=com.lucidworks.cloud.search.solr.customer")
        .header("authorization", "Basic MG9hY3FobHJoU3U1Q0k1ODkzNTY6bnZhZmtBVUxoeEJCc1JXUGZKRmtXR0JVd1J3bVZCV1lhaHpxak0zdQ==")
        .header("accept", "application/json")
        .header("cache-control", "no-cache")
        .header("content-type", "application/x-www-form-urlencoded")
        .execute(parser = {inputStream => jsonObjectMapper.readTree(inputStream)})
      if (!jsonResp.is2xx) throw new RuntimeException(s"Failed to login to ${loginUrl} due to: ${jsonResp.code}")
      jwtToken = jsonResp.body.get("access_token").asText()
      val expires_in = jsonResp.body.get("expires_in").asLong()
      val grace_secs = if (expires_in > 15L) 10L else 2L
      jwtExpiresIn = expires_in - grace_secs
      println(s"Successfully refreshed global JWT for load test ... will do again in ${jwtExpiresIn} secs")
    }

    // This function is rife with side-effects ;-)
    def initJwtAndStartBgRefreshThread(): Unit = {

      // Get the initial token ...
      updateJwtToken()
      println(s"Received initial JWT from POST to https://pg01.us-west1.cloud.lucidworks.com/oauth2/token: ${jwtToken}\n")

      // Schedule a background task to refresh it before the token expires
      // Make the thread a daemon so the JVM can exit
      class DaemonFactory extends ThreadFactory {
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r)
          t.setDaemon(true)
          t
        }
      }
      val ex = Executors.newSingleThreadScheduledExecutor(new DaemonFactory)
      val task = new Runnable {
        def run(): Unit = updateJwtToken()
      }
      ex.scheduleAtFixedRate(task, jwtExpiresIn, jwtExpiresIn, TimeUnit.SECONDS)
      println(s"Started background thread to refresh JWT in ${jwtExpiresIn} seconds from now ...\n")
    }

  }

  object Query {
    // construct a feeder for our query params stored in the csv

    Config.initJwtAndStartBgRefreshThread()

    val feeder = tsv(Config.queryFeederSource).circular

    val saveGlobalJWTInSession = exec { session => session.set("jwt", Config.jwtToken) }

    // each user sends loops queries
    val search = feed(feeder).exec(
      http("QueryRequest").
        get(Config.solrUrl + "/" + Config.defaultCollection + "/select?" + Config.basequery).
        header("Authorization", "Bearer ${jwt}").check(status.in(200, 204)))

  }

  val clientId: Option[String] = Option(System.getenv("OAUTH2_CLIENT_ID"))
  val oauth2ClientId: String = if (clientId.isDefined) clientId.get else System.getProperty("OAUTH2_CLIENT_ID")
  val clientSecret: Option[String] = Option(System.getenv("OAUTH2_CLIENT_SECRET"))
  val oauth2ClientSecret: String = if (clientSecret.isDefined) clientSecret.get else System.getProperty("OAUTH2_CLIENT_SECRET")

  // create http request interceptor and start it
  val oauth2HttpRequestInterceptor: OAuth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(Config.oauth2CustomerId, oauth2ClientId, oauth2ClientSecret).build
  oauth2HttpRequestInterceptor.start()
  oauth2HttpRequestInterceptor.awaitFirstRefresh(60, TimeUnit.SECONDS);

  // register http request interceptor with solrj
  HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor)

  //  val client = new CloudSolrClient.Builder(Collections.singletonList(Config.solrUrl)).build()
  //  client.setDefaultCollection(Config.defaultCollection)
  //  client.commit(false, true)

  // pass zookeeper string, default collection to query, poolSize for CloudSolrClients
  val solrConf: SolrProtocol = solr.solrurl(Config.solrUrl).collection(Config.defaultCollection).numClients(Config.numClients.toInt).properties(Config.prop).
    authClientId(oauth2ClientId).authClientSecret(oauth2ClientSecret)

  // A scenario where users execute queries
  val query: ScenarioBuilder = scenario("QUERY").exec(Query.saveGlobalJWTInSession).exec(Query.search)

    setUp(
      query.inject(
        rampUsersPerSec(Config.queryMinNumUsers.toDouble) to Config.queryMaxNumUsers.toDouble during
          (Config.totalTimeInMinutes.toDouble minutes)).protocols(solrConf)
    ).maxDuration((Config.totalTimeInMinutes.toInt + 5) minutes)
}