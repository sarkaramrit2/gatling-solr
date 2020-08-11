package lucidworks.gatling.solr.request.builder

import java.util

import lucidworks.gatling.solr.action._
import io.gatling.core.session._
import lucidworks.gatling.solr.action.{ManagedSolrQueryRequestActionBuilder, SolrIndexRequestActionBuilder, SolrQueryRequestActionBuilder}
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

  def managedIndexV1[K, V](header: String, payload: Expression[String]): ManagedSolrIndexV1RequestActionBuilder[K, V] =
    new ManagedSolrIndexV1RequestActionBuilder(SolrIndexAttributes(requestName, header, payload))

  def managedQuery[K](payload: Expression[String]): ManagedSolrQueryRequestActionBuilder[K] =
    new ManagedSolrQueryRequestActionBuilder(SolrQueryAttributes(requestName, payload))
}
