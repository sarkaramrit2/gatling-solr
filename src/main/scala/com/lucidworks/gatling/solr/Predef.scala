package com.lucidworks.gatling.solr

import com.lucidworks.gatling.solr.protocol.SolrProtocolBuilder
import com.lucidworks.gatling.solr.request.builder.SolrRequestBuilder
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Expression


object Predef {

  def solr(implicit configuration: GatlingConfiguration) = SolrProtocolBuilder(configuration)

  def solr(requestName: Expression[String]) = new SolrRequestBuilder(requestName)

}