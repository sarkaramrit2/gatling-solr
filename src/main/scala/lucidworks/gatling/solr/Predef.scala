package lucidworks.gatling.solr

import lucidworks.gatling.solr.protocol.SolrProtocolBuilder
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Expression
import lucidworks.gatling.solr.protocol.SolrProtocolBuilder
import lucidworks.gatling.solr.request.builder.SolrRequestBuilder


object Predef {

  def solr(implicit configuration: GatlingConfiguration) = SolrProtocolBuilder(configuration)

  def solr(requestName: Expression[String]) = new SolrRequestBuilder(requestName)

}