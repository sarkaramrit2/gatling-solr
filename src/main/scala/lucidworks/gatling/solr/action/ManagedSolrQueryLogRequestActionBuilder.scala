package lucidworks.gatling.solr.action

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

import com.lucidworks.cloud.{ManagedSearchClusterStateProvider, OAuth2HttpRequestInterceptor, OAuth2HttpRequestInterceptorBuilder}
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.structure.ScenarioContext
import lucidworks.gatling.solr.protocol.{SolrComponents, SolrProtocol}
import lucidworks.gatling.solr.request.builder.SolrQueryAttributes
import org.apache.commons.io.IOUtils
import org.apache.solr.client.solrj.impl.{CloudSolrClient, HttpClientUtil}


class ManagedSolrQueryLogRequestActionBuilder[K](solrAttributes: SolrQueryAttributes[K]) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    import ctx.{coreComponents, protocolComponentsRegistry, throttled}

    val solrComponents: SolrComponents = protocolComponentsRegistry.components(SolrProtocol.SolrProtocolKey)

    val solrClients = new util.ArrayList[CloudSolrClient]()

    var solrClient = null: CloudSolrClient;

    // create http request interceptor and start it
    val oauth2HttpRequestInterceptor: OAuth2HttpRequestInterceptor = new OAuth2HttpRequestInterceptorBuilder(solrComponents.solrProtocol.customerId,
      solrComponents.solrProtocol.authClientId, solrComponents.solrProtocol.authClientSecret).build
    oauth2HttpRequestInterceptor.start()
    oauth2HttpRequestInterceptor.awaitFirstRefresh(60, TimeUnit.SECONDS);

    // register http request interceptor with solrj
    HttpClientUtil.addRequestInterceptor(oauth2HttpRequestInterceptor)

    for (i <- 0 until solrComponents.solrProtocol.numClients) {
      solrClient = new CloudSolrClient.Builder(new ManagedSearchClusterStateProvider(Collections.singletonList(solrComponents.solrProtocol.solrurl))).build
      solrClient.setDefaultCollection(solrComponents.solrProtocol.collection)
      solrClients.add(solrClient)
    }

    coreComponents.actorSystem.registerOnTermination(
      for (i <- 0 until solrComponents.solrProtocol.numClients) {
        if (i == 0) {
          // remove the interceptor from the request chain
          HttpClientUtil.removeRequestInterceptor(oauth2HttpRequestInterceptor)
          // close the http request interceptor to stop background token refresh
          IOUtils.closeQuietly(oauth2HttpRequestInterceptor)
        }
        solrClients.get(i).close()
      }
    )

    new SolrQueryLogRequestAction(
      solrClients,
      solrAttributes,
      coreComponents,
      solrComponents.solrProtocol,
      throttled,
      next
    )

  }

}