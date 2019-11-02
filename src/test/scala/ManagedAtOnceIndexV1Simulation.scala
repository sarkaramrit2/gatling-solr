import java.io.{File, FileReader}
import java.util.{Properties, Scanner}

import com.lucidworks.cloud.{OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import lucidworks.gatling.solr.Predef._
import org.apache.solr.client.solrj.impl.HttpClientUtil

import scala.util.control.Breaks.break

class ManagedAtOnceIndexV1Simulation extends Simulation {

  object Config {

    import java.io.FileInputStream

    val prop: Properties = new Properties
    val propFile = new FileInputStream("/opt/gatling/user-files/" +
      "configs/index.config.properties");
    prop.load(propFile)

    val indexFilePath = prop.getProperty("indexFilePath", "/opt/gatling/user-files/" +
      "data/enwiki.random.lines.csv.txt")
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

  // register http request interceptor with solrj
  HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor)

  // pass zookeeper string, default collection to index, poolSize for CloudSolrClients
  val solrConf = solr.solrurl(Config.solrUrl).collection(Config.defaultCollection)
    .numClients(Config.numClients.toInt).properties(Config.prop)

  // A scenario where users execute queries
  val users = scenario("Users").exec(Index.search)

  setUp(
    users.inject(
      atOnceUsers(Config.maxNumUsers.toInt))
  ).protocols(solrConf)

}