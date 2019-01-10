package com.lucidworks.gatling.solr.request.builder

import java.util

import com.lucidworks.gatling.solr.action.{SolrIndexRequestActionBuilder, SolrIndexV2RequestActionBuilder, SolrQueryRequestActionBuilder}
import io.gatling.core.session._
import org.apache.solr.common.SolrInputDocument

case class SolrQueryAttributes[K](requestName: Expression[String],
                                  payload: Expression[String])

case class SolrIndexAttributes[K, V](requestName: Expression[String],
                                       header: String,
                                       payload: Expression[String]
                                    )

case class SolrIndexV2Attributes[K, V](requestName: Expression[String],
                                     header: String,
                                     payload: util.ArrayList[SolrInputDocument]
                                    )

case class SolrRequestBuilder(requestName: Expression[String]) {

  def query[K](payload: Expression[String]): SolrQueryRequestActionBuilder[K] =
    new SolrQueryRequestActionBuilder(SolrQueryAttributes(requestName, payload))

  def index[K, V](header: String, payload: Expression[String]): SolrIndexRequestActionBuilder[K, V] =
    new SolrIndexRequestActionBuilder(SolrIndexAttributes(requestName, header, payload))

  def indexV2[K, V](header: String, payload: util.ArrayList[SolrInputDocument]): SolrIndexV2RequestActionBuilder[K, V] =
    new SolrIndexV2RequestActionBuilder(SolrIndexV2Attributes(requestName, header, payload))

}
