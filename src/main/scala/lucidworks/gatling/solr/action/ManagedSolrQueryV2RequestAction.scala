package lucidworks.gatling.solr.action

import java.nio.charset.Charset
import java.util.Collections

import com.lucidworks.cloud.ManagedSearchClusterStateProvider
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.DefaultClock
import io.gatling.commons.validation.Validation
import io.gatling.core.CoreComponents
import io.gatling.core.action.{Action, ExitableAction}
import io.gatling.core.session._
import io.gatling.core.stats.StatsEngine
import io.gatling.core.util.NameGen
import lucidworks.gatling.solr.protocol.SolrProtocol
import lucidworks.gatling.solr.request.builder.SolrQueryAttributes
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.solr.client.solrj.impl.CloudSolrClient
import org.apache.solr.client.solrj.response.QueryResponse
import org.apache.solr.common.params.ModifiableSolrParams

import scala.collection.JavaConversions._


class ManagedSolrQueryV2RequestAction[K, V](val solrAttributes: SolrQueryAttributes[V],
                                            val coreComponents: CoreComponents,
                                            val solrProtocol: SolrProtocol,
                                            val throttled: Boolean,
                                            val next: Action)
  extends ExitableAction with NameGen {

  override val name: String = genName("solrQueryRequest")
  val statsEngine: StatsEngine = coreComponents.statsEngine
  val clock = new DefaultClock

  override def execute(session: Session): Unit = recover(session) {

    solrAttributes requestName session flatMap { requestName =>

      var solrClient= null: CloudSolrClient;
      solrClient = new CloudSolrClient.Builder(new ManagedSearchClusterStateProvider(Collections.singletonList(solrProtocol.solrurl))).build
      solrClient.setDefaultCollection(solrProtocol.collection)

      val outcome =
        sendRequest(
          requestName,
          solrClient, // round robin for solrclients
          solrAttributes,
          throttled,
          session)

      outcome.onFailure(
        errorMessage =>
          statsEngine.reportUnbuildableRequest(session, requestName, errorMessage)
      )

      solrClient.close()
      outcome

    }

  }

  private def sendRequest(requestName: String,
                          solrClient: CloudSolrClient,
                          solrAttributes: SolrQueryAttributes[V],
                          throttled: Boolean,
                          session: Session): Validation[Unit] = {

    solrAttributes payload session map { payload =>

      val params = new ModifiableSolrParams();
      for (param <- URLEncodedUtils.parse(payload, Charset.forName("UTF-8"))) {
        params.add(param.getName, param.getValue)
      }

      val requestStartDate = clock.nowMillis
      var response: QueryResponse = null
      try {
        response = solrClient.query(params)
      }
      catch {
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
          next ! session
        }
      }
      val requestEndDate = clock.nowMillis

      if (response != null) {
        statsEngine.logResponse(
          session,
          requestName,
          startTimestamp = requestStartDate,
          endTimestamp = requestEndDate,
          if (response.getException == null) OK else KO,
          None,
          if (response.getException == null) None else Some(response.getException.getMessage)
        )
      }
      next ! session
    }
  }
}
