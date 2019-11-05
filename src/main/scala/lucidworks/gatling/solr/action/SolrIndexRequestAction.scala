package lucidworks.gatling.solr.action

import java.util
import java.util.Properties

import lucidworks.gatling.solr.protocol.SolrProtocol
import lucidworks.gatling.solr.utils.Constants
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.DefaultClock
import io.gatling.commons.validation.Validation
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.util.NameGen
import lucidworks.gatling.solr.protocol.SolrProtocol
import lucidworks.gatling.solr.request.builder.SolrIndexAttributes
import lucidworks.gatling.solr.utils.Constants
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.request.UpdateRequest
import org.apache.solr.common.SolrInputDocument


class SolrIndexRequestAction[K, V](val solrClients: util.ArrayList[CloudSolrClient],
                                   val properties: Properties,
                                   val solrAttributes: SolrIndexAttributes[K, V],
                                   val coreComponents: CoreComponents,
                                   val solrProtocol: SolrProtocol,
                                   val throttled: Boolean,
                                   val next: Action)
  extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine
  val clock = new DefaultClock
  override val name = genName("solrIndexRequest")

  override def execute(session: Session): Unit = recover(session) {

    solrAttributes requestName session flatMap { requestName =>

      val outcome =
        sendRequest(
          requestName,
          solrClients.get((session.userId % solrClients.size).toInt), // round robin for solrclients
          properties,
          solrAttributes,
          throttled,
          session)

      outcome.onFailure(
        errorMessage =>
          statsEngine.reportUnbuildableRequest(session, requestName, errorMessage)
      )

      outcome

    }

  }

  private def sendRequest(requestName: String,
                          solrClient: CloudSolrClient,
                          prop: Properties,
                          solrAttributes: SolrIndexAttributes[K, V],
                          throttled: Boolean,
                          session: Session): Validation[Unit] = {

    solrAttributes payload session map { payload =>

      val updateRequest = new UpdateRequest()
      val fieldNames = solrAttributes.header.split(prop.getProperty(Constants.HEADER_SEP, ",")) // default comma

      val lines = payload.split(prop.getProperty(Constants.LINES_SEP, "\n")) // default new line char
      val docs = new util.ArrayList[SolrInputDocument]()

      for (j <- 0 until lines.length) {
        val doc = new SolrInputDocument()
        val fieldValues = lines(j).split(prop.getProperty(Constants.FIELDVALUES_SEP, ",")) // default comma

        for (i <- 0 until fieldNames.length) {
          if (fieldValues.length - 1 >= i) {
            if (prop.getProperty(Constants.MULTIPARAM_SEP, "#") != null) {
              val multiValues = fieldValues(i).trim.split(prop.getProperty(Constants.MULTIPARAM_SEP, "#"));
              for (j <- 0 until multiValues.length) {
                doc.addField(fieldNames(i), multiValues(j).trim);
              }
            }
            else {
              doc.addField(fieldNames(i), fieldValues(i))
            }
          }
        }
        docs.add(doc)
      }

      updateRequest.add(docs)

      val requestStartDate = clock.nowMillis

      try {
        solrClient.request(updateRequest)
        val requestEndDate = clock.nowMillis
        statsEngine.logResponse(
          session,
          requestName,
          requestStartDate,
          requestEndDate,
          OK,
          None,
          None
        )
      } catch {
        case ex: Exception => {
          val requestEndDate = clock.nowMillis
          statsEngine.logResponse(
            session,
            requestName,
            requestStartDate,
            requestEndDate,
            KO,
            None,
            Option(ex.getMessage)
          )

        }
      }

      next ! session

    }

  }

}
