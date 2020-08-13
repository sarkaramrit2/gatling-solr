import java.io.{File, FileReader}
import java.lang.invoke.MethodHandles
import java.net.URL
import java.util.{Properties, Scanner}

import io.gatling.core.Predef._
import io.gatling.core.feeder.Feeder
import lucidworks.gatling.solr.Predef._
import org.slf4j.{LoggerFactory}

import scala.concurrent.duration._
import scala.util.control.Breaks.break

class UpdateAndRampUpQuerySimulation extends Simulation {

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

  val solrIndexV1Feeder = new Feeder[String] {

    private var podNo = Config.podNo.toInt
    private var indexFile: File = _
    private var url: URL = _
    if (Config.indexTotalFiles.toInt <= 1) {
      if (Config.indexUrlPath != null) {
        System.out.println("indexUrl: " + Config.indexUrlPath)
        log.info("indexUrl: " + Config.indexUrlPath)
        url = new URL(Config.indexUrlPath)
      }
      else {
        System.out.println("indexFile: " + Config.indexFilePath)
        log.info("indexUrl: " + Config.indexUrlPath)
        indexFile = new File(Config.indexFilePath)
      }
    }
    else {
      if (Config.indexUrlPath != null) {
        System.out.println("indexUrl: " + Config.indexUrlPath + Config.podNo)
        log.info("indexUrl: " + Config.indexUrlPath  + Config.podNo)
        url = new URL(Config.indexUrlPath + Config.podNo)
      }
      else {
        System.out.println("indexFile: " + Config.indexFilePath + Config.podNo)
        log.info("indexFile: " + Config.indexFilePath + Config.podNo)

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
      if (Config.indexTotalFiles.toInt <= 1) {
        false
      }
      else {
        scanner.close()
        if (Config.indexUrlPath == null) {
          fileReader.close()
        }
        if (podNo + Config.indexParallelNodes.toInt > Config.indexTotalFiles.toInt) {
          podNo = (podNo + Config.indexParallelNodes.toInt)
          podNo = Math.floorMod(podNo, Config.indexTotalFiles.toInt)
        }
        else {
          podNo = podNo + Config.indexParallelNodes.toInt
        }
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


  val solrUpdateV1Feeder = new Feeder[String] {

    private var podNo = Config.podNo.toInt
    private var updateFile: File = _
    private var url: URL = _
    if (Config.updateTotalFiles.toInt <= 1) {
      if (Config.updateUrlPath != null) {
        System.out.println("updateUrl: " + Config.updateUrlPath)
        log.info("updateUrl: " + Config.updateUrlPath)
        url = new URL(Config.updateUrlPath)
      }
      else {
        System.out.println("updateFile: " + Config.updateFilePath)
        log.info("updateFile: " + Config.updateFilePath)
        updateFile = new File(Config.updateFilePath)
      }
    }
    else {
      if (Config.updateUrlPath != null) {
        System.out.println("updateUrl: " + Config.updateUrlPath + Config.podNo)
        log.info("updateUrl: " + Config.updateUrlPath + Config.podNo)
        url = new URL(Config.updateUrlPath + Config.podNo)
      }
      else {
        System.out.println("updateFile: " + Config.updateFilePath + Config.podNo)
        log.info("updateFile: " + Config.updateFilePath + Config.podNo)
        updateFile = new File(Config.updateFilePath + Config.podNo)
      }
    }

    private var fileReader: FileReader = _
    private var scanner: Scanner = _
    if (Config.updateUrlPath != null) {
      scanner = new Scanner(url.openStream())
    }
    else {
      fileReader = new FileReader(updateFile)
      scanner = new Scanner(fileReader)
    }

    override def hasNext = if (!scanner.hasNext) {
      if (Config.updateTotalFiles.toInt <= 1) {
        false
      }
      else {
        scanner.close()
        if (Config.updateUrlPath == null) {
          fileReader.close()
        }
        if (podNo + Config.updateParallelNodes.toInt > Config.updateTotalFiles.toInt) {
          podNo = (podNo + Config.updateParallelNodes.toInt)
          podNo = Math.floorMod(podNo, Config.updateTotalFiles.toInt)
        }
        else {
          podNo = podNo + Config.updateParallelNodes.toInt
        }
        if (Config.updateUrlPath != null) {
          url = new URL(Config.updateUrlPath + Config.podNo)
          scanner = new Scanner(url.openStream())
        }
        else {
          updateFile = new File(Config.updateFilePath + Config.podNo)
          fileReader = new FileReader(updateFile)
          scanner = new Scanner(fileReader)
        }
        true
      }
    }
    else {
      true
    }

    override def next: Map[String, String] = {
      var batchSize = Config.updateBatchSize.toInt
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
      feed(feeder).exec(solr("IndexV1Request")
        .index(Config.header, "${record}")) // provide appropriate header
    }
  }

  object Update {
    // construct a feeder for content stored in CSV file
    val feeder = solrUpdateV1Feeder

    // each user sends batches
    val search = repeat(Config.updateNumBatchesPerUser) {
      feed(feeder).exec(solr("UpdateV1Request")
        .index(Config.header, "${record}")) // provide appropriate header
    }
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

  val index = scenario("INDEX").exec(Index.search)

  val update = scenario("UPDATE").exec(Update.search)

  System.out.println("podNo: " + Config.podNo.toInt)
  log.info("podNo: " + Config.podNo.toInt)

  var indexExecute: Boolean = false
  var updateExecute: Boolean = false

  if (Config.indexParallelNodes.toInt > 1 && Config.indexTotalFiles.toInt > 1) {
    if (Config.podNo.toInt < Config.indexParallelNodes.toInt && Config.podNo.toInt <= Config.indexTotalFiles.toInt) {
      indexExecute = true
    }
  }

  if (Config.updateParallelNodes.toInt > 1 && Config.updateTotalFiles.toInt > 1) {
    if (Config.podNo.toInt < Config.updateParallelNodes.toInt && Config.podNo.toInt <= Config.updateTotalFiles.toInt) {
      updateExecute = true
    }
  }

  if (indexExecute && updateExecute) {
    setUp(
      index.inject(
        constantUsersPerSec(Config.indexMaxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
        .protocols(solrConf),
      update.inject(
        constantUsersPerSec(Config.updateMaxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
        .protocols(solrConf)
    )
  }
  else if (!indexExecute && updateExecute) {
    setUp(
      update.inject(
        constantUsersPerSec(Config.updateMaxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
        .protocols(solrConf)
    )
  }
  else if (indexExecute && !updateExecute) {
    setUp(
      index.inject(
        constantUsersPerSec(Config.indexMaxNumUsers.toDouble) during (Config.totalTimeInMinutes.toDouble minutes))
        .protocols(solrConf)
    )
  }
  else {
    setUp(
      query.inject(
        rampUsersPerSec(Config.queryMinNumUsers.toDouble) to Config.queryMaxNumUsers.toDouble during
          (Config.totalTimeInMinutes.toDouble minutes)).protocols(solrConf)
    )
  }
}