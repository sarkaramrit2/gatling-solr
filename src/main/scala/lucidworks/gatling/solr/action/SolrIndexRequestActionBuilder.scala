package lucidworks.gatling.solr.action

import java.util

import lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import lucidworks.gatling.solr.request.builder.SolrIndexAttributes
import org.apache.solr.client.solrj.impl.CloudSolrClient


class SolrIndexRequestActionBuilder[K, V](solrAttributes: SolrIndexAttributes[K, V]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()

    var clientsCount = solrComponents.solrProtocol.numClients

    if (solrComponents.solrProtocol.numIndexClients > 0 ) {
      clientsCount = solrComponents.solrProtocol.numIndexClients
    }


    for( i <- 0 until clientsCount){
      var solrClient= null: CloudSolrClient;
      solrClient = new CloudSolrClient.Builder().withZkHost(solrComponents.solrProtocol.zkhost).build()
      solrClient.setDefaultCollection(solrComponents.solrProtocol.collection)
      solrClients.add(solrClient)
    }

    coreComponents.actorSystem.registerOnTermination(
      for( i <- 0 until solrComponents.solrProtocol.numClients){
        solrClients.get(i).close()
      }
    )

    new SolrIndexRequestAction(
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