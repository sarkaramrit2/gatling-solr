import java.io.{File, FileReader}
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.{Collections, Properties, Scanner}

import com.lucidworks.cloud.{ManagedSearchClusterStateProvider, OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import lucidworks.gatling.solr.Predef._
import org.apache.solr.client.solrj.impl.{CloudSolrClient, HttpClientUtil}

import scala.concurrent.duration.DurationDouble
import scala.util.control.Breaks.break

class ManagedConstantIndexV1Simulation extends Simulation {

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/index.config.properties");
    prop.load(propFile)

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv.txt")
    val indexUrlPath = prop.getProperty("indexUrlPath", null)
    val numBatchesPerUser = prop.getProperty("numBatchesPerUser", "1")
    val maxNumUsers = prop.getProperty("maxNumUsers", "1")
    val minNumUsers = prop.getProperty("minNumUsers", "1")
    val totalTimeInMinutes = prop.getProperty("totalTimeInMinutes", "34")
    val indexBatchSize = prop.getProperty("indexBatchSize", "5000")
    val zkHost = prop.getProperty("zkHost", "localhost:9983")
    val solrUrl = prop.getProperty("solrUrl", "http://localhost:8983/solr/")
    val defaultCollection = prop.getProperty("defaultCollection", "wiki")
    val header = prop.getProperty("header", "id,title,time,description")
    val headerSep = prop.getProperty("header.sep", ",")
    val multiParamSep = prop.getProperty("multiParam.sep", null)
    val fieldValuesSep = prop.getProperty("fieldValues.sep", ",")
    val numClients = prop.getProperty("numClients", "9")
    val oauth2CustomerId = prop.getProperty("CUSTOMER_ID", "lucidworks")
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
    private var url: URL = _
    if (Config.totalFiles.toInt <= 1) {
      if (Config.indexUrlPath != null) {
        System.out.println("indexUrl: " + Config.indexUrlPath)
        url = new URL(Config.indexUrlPath)
      }
      else {
        System.out.println("indexFile: " + Config.indexFilePath)
        indexFile = new File(Config.indexFilePath)
      }
    }
    else {
      if (Config.indexUrlPath != null) {
        System.out.println("indexUrl: " + Config.indexUrlPath + Config.podNo)
        url = new URL(Config.indexUrlPath + Config.podNo)
      }
      else {
        System.out.println("indexFile: " + Config.indexFilePath + Config.podNo)
        indexFile = new File(Config.indexFilePath + Config.podNo)
      }
    }

    private var fileReader: FileReader = _
    private var scanner: Scanner = _
    if (Config.indexUrlPath != null) {
      scanner = new Scanner(url.openStream())
    }
    else {
      fileReader = new FileReader(indexFile)
      scanner = new Scanner(fileReader)
    }

    override def hasNext = if (!scanner.hasNext) {
      if (Config.totalFiles.toInt <= 1) {
        false
      }
      else {
        if (podNo + Config.parallelNodes.toInt > Config.totalFiles.toInt) {
          false
        }
        else {
          scanner.close()
          if (Config.indexUrlPath == null) {
            fileReader.close()
          }
          podNo = podNo + Config.parallelNodes.toInt
          if (Config.indexUrlPath != null) {
            url = new URL(Config.indexUrlPath + Config.podNo)
            scanner = new Scanner(url.openStream())
          }
          else {
            indexFile = new File(Config.indexFilePath + Config.podNo)
            fileReader = new FileReader(indexFile)
            scanner = new Scanner(fileReader)
          }
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
      feed(feeder).exec(solr("managedSearchIndexV1Request")
        .managedIndexV1(Config.header, "${record}")) // provide appropriate header
    }
  }

  System.out.println("pod name: " + System.getenv("POD_NAME"))

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
  val solrConf = solr.solrurl(Config.solrUrl).customerId(Config.oauth2CustomerId).collection(Config.defaultCollection).numClients(Config.numClients.toInt).
    properties(Config.prop).authClientId(oauth2ClientId).authClientSecret(oauth2ClientSecret)

  // A scenario where users execute queries
  val users = scenario("Users").exec(Index.search)

  setUp(
    users.inject(
      constantUsersPerSec(Config.maxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
  ).protocols(solrConf)

}