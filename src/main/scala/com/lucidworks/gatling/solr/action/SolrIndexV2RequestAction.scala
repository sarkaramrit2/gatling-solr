package com.lucidworks.gatling.solr.action

import java.util
import java.util.Properties

import com.lucidworks.gatling.solr.protocol.SolrProtocol
import com.lucidworks.gatling.solr.request.builder.SolrIndexV2Attributes
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.DefaultClock
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.util.NameGen
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.request.UpdateRequest


class SolrIndexV2RequestAction[K, V](val solrClients: util.ArrayList[CloudSolrClient],
                                     val properties: Properties,
                                     val solrAttributes: SolrIndexV2Attributes[K, V],
                                     val coreComponents: CoreComponents,
                                     val solrProtocol: SolrProtocol,
                                     val throttled: Boolean,
                                     val next: Action)
  extends ExitableAction with NameGen {

  val statsEngine = coreComponents.statsEngine
  val clock = new DefaultClock
  override val name = genName("solrIndexRequest")

  override def execute(session: Session) {
    sendRequest(
      name,
      solrClients.get((session.userId % solrClients.size).toInt),
      // round robin for solrclients
      properties,
      solrAttributes,
      throttled,
      session)
  }

  private def sendRequest(requestName: String,
                          solrClient: CloudSolrClient,
                          prop: Properties,
                          solrAttributes: SolrIndexV2Attributes[K, V],
                          throttled: Boolean,
                          session: Session) {

    val updateRequest = new UpdateRequest()
    updateRequest.add(solrAttributes.payload)

    val requestStartDate = clock.nowMillis
    val response = solrClient.request(updateRequest)

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
