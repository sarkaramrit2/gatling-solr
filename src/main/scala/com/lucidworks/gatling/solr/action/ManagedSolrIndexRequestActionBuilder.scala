package com.lucidworks.gatling.solr.action

import java.util

import com.lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import com.lucidworks.gatling.solr.request.builder.SolrIndexV2Attributes
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import org.apache.http.HttpRequest
import org.apache.http.protocol.HttpContext
import org.apache.solr.client.solrj.impl.{CloudSolrClient, HttpClientUtil}
import java.util.Base64
import java.util.Collections


class ManagedSolrIndexRequestActionBuilder[K, V](solrAttributes: SolrIndexV2Attributes[K, V]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()

    val cred = Base64.getEncoder.encodeToString((solrComponents.solrProtocol.apikey + ":x").getBytes)
    HttpClientUtil.addRequestInterceptor((request: HttpRequest, context: HttpContext) => {
      def foo(request: HttpRequest, context: HttpContext) = request.setHeader("Authorization", "Basic " + cred)
      foo(request, context)
    })

    for( i <- 0 until solrComponents.solrProtocol.numClients){

      var solrClient= null: CloudSolrClient;
      val client = HttpClientUtil.createClient(null)
      solrClient = new CloudSolrClient.Builder(Collections.singletonList(solrComponents.solrProtocol.solrurl)).withHttpClient(client).build
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