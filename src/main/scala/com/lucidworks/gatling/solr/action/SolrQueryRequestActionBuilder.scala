package com.lucidworks.gatling.solr.action

import java.util

import com.lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import com.lucidworks.gatling.solr.request.builder.SolrQueryAttributes
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import org.apache.solr.client.solrj.impl.CloudSolrClient


class SolrQueryRequestActionBuilder[K](solrAttributes: SolrQueryAttributes[K]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()

    var solrClient= null: CloudSolrClient;
    solrClient = new CloudSolrClient.Builder().withZkHost(solrComponents.solrProtocol.zkhost).build()
    solrClient.setDefaultCollection(solrComponents.solrProtocol.collection)
    solrClients.add(solrClient)

    coreComponents.actorSystem.registerOnTermination(
      for( i <- 0 until solrComponents.solrProtocol.numClients){
        solrClients.get(i).close()
      }
    )

    new SolrQueryRequestAction(
      solrClients,
      solrAttributes,
      coreComponents,
      solrComponents.solrProtocol,
      throttled,
      next
    )

  }

}