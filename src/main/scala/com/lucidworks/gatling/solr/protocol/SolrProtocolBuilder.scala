package com.lucidworks.gatling.solr.protocol

import io.gatling.core.config.GatlingConfiguration

object SolrProtocolBuilder {

  implicit def toSolrProtocol(builder: SolrProtocolBuilder): SolrProtocol = builder.build

  def apply(configuration: GatlingConfiguration): SolrProtocolBuilder =
    SolrProtocolBuilder(SolrProtocol(configuration))

}

case class SolrProtocolBuilder(solrProtocol: SolrProtocol) {

  def build = solrProtocol

}