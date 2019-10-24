import java.io.{BufferedReader, File, FileReader}
import java.util
import java.util.Properties

import com.lucidworks.cloud.{OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import lucidworks.gatling.solr.Predef._
import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import org.apache.solr.client.solrj.impl.HttpClientUtil
import org.apache.solr.common.SolrInputDocument

import scala.concurrent.duration._


class ManagedIndexSimulation extends Simulation {

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
    val multiParamSep = prop.getProperty("multiParam.sep", "\t")
    val fieldValuesSep = prop.getProperty("fieldValues.sep", ",")
    val numClients = prop.getProperty("numClients", "9")

  }

  val solrIndexV2Feeder = new Feeder[util.ArrayList[SolrInputDocument]] {

    private var indexFile = new File(Config.indexFilePath)
    private var fileReader = new FileReader(indexFile)
    private var reader = new BufferedReader(fileReader)

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
      feed(feeder).exec(solr("indexConstantUsersRequest")
        .managedIndex(Config.header, feeder.next.get("record").get)) // provide appropriate header
    }
  }

  val clientId = Option(System.getenv("OAUTH2_CLIENT_ID"))
  val oauth2ClientId: String = if (clientId.isDefined) clientId.get else System.getProperty("OAUTH2_CLIENT_ID")
  val clientSecret = Option(System.getenv("OAUTH2_CLIENT_SECRET"))
  val oauth2ClientSecret: String = if (clientSecret.isDefined) clientSecret.get else System.getProperty("OAUTH2_CLIENT_SECRET")
  val customerId = Option(System.getenv("CUSTOMER_ID"))
  val oauth2CustomerId: String = if (clientSecret.isDefined) customerId.get else System.getProperty("CUSTOMER_ID")

  // create http request interceptor and start it
  val oauth2HttpRequestInterceptor: OAuth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(oauth2CustomerId, oauth2ClientId, oauth2ClientSecret).build
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
      constantUsersPerSec(Config.maxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
  ).protocols(solrConf)

}