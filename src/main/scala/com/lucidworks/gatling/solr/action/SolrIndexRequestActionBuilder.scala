package com.lucidworks.gatling.solr.action

import java.util

import com.lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import com.lucidworks.gatling.solr.request.builder.SolrIndexAttributes
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.solr.client.solrj.impl.CloudSolrClient


class SolrIndexRequestActionBuilder[K, V](solrAttributes: SolrIndexAttributes[K, V]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val httpBuilder = HttpClients.custom()
    httpBuilder.setDefaultHeaders(util.Arrays.asList(new BasicHeader("X-Default-Header", "default header")))
    httpBuilder.setDefaultRequestConfig(RequestConfig.custom.setExpectContinueEnabled(true).build())
    val httpClient = httpBuilder.build()

    val solrClients = new util.ArrayList[CloudSolrClient]()

    for( i <- 0 until solrComponents.solrProtocol.numClients){
      var solrClient= null: CloudSolrClient;
      if (solrComponents.solrProtocol.zkhost != null & !solrComponents.solrProtocol.zkhost.isEmpty) {
        solrClient = new CloudSolrClient.Builder().withZkHost(solrComponents.solrProtocol.zkhost).withHttpClient(httpClient).build()
      }
      else if (solrComponents.solrProtocol.solrurl != null & !solrComponents.solrProtocol.solrurl.isEmpty) {
        solrClient = new CloudSolrClient.Builder().withSolrUrl(solrComponents.solrProtocol.solrurl).withHttpClient(httpClient).build()
      }
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