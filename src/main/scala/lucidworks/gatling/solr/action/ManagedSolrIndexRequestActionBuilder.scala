package lucidworks.gatling.solr.action

import java.util

import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import org.apache.http.HttpRequest
import org.apache.http.protocol.HttpContext
import org.apache.solr.client.solrj.impl.{CloudSolrClient, HttpClientUtil}
import java.util.Base64
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.lucidworks.cloud.{OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import lucidworks.gatling.solr.request.builder.SolrIndexV2Attributes


class ManagedSolrIndexRequestActionBuilder[K, V](solrAttributes: SolrIndexV2Attributes[K, V]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()
    for( i <- 0 until solrComponents.solrProtocol.numClients){
      var solrClient= null: CloudSolrClient
      solrClient = new CloudSolrClient.Builder(Collections.singletonList(solrComponents.solrProtocol.solrurl)).build
      solrClient.setDefaultCollection(solrComponents.solrProtocol.collection)
      solrClients.add(solrClient)
    }

    coreComponents.actorSystem.registerOnTermination(
      for( i <- 0 until solrComponents.solrProtocol.numClients){
        solrClients.get(i).close()
      }
    )

    new SolrIndexV2RequestAction(
      solrClients,
      solrComponents.solrProtocol.properties,
      solrAttributes,
      coreComponents,
      solrComponents.solrProtocol,
      throttled,
      next
    )

  }

}