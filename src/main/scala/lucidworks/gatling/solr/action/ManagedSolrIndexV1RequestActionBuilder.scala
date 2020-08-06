package lucidworks.gatling.solr.action

import java.util
import java.util.Collections

import com.lucidworks.cloud.ManagedSearchClusterStateProvider
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import lucidworks.gatling.solr.request.builder.SolrIndexAttributes
import org.apache.solr.client.solrj.impl.CloudSolrClient


class ManagedSolrIndexV1RequestActionBuilder[K, V](solrAttributes: SolrIndexAttributes[K, V]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()

    for( i <- 0 until solrComponents.solrProtocol.numClients){
      var solrClient= null: CloudSolrClient;
      solrClient = new CloudSolrClient.Builder(new ManagedSearchClusterStateProvider(Collections.singletonList(solrComponents.solrProtocol.solrurl))).build
      solrClient.setDefaultCollection(solrComponents.solrProtocol.collection)
      solrClients.add(solrClient)
    }

    coreComponents.actorSystem.registerOnTermination(
      for( i <- 0 until solrComponents.solrProtocol.numClients){
        solrClients.get(i).close()
      }
    )

    new ManagedSolrIndexV1RequestAction(
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