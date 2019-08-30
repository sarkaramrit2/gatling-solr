package lucidworks.gatling.solr.protocol

import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session


case class SolrComponents(solrProtocol: SolrProtocol) extends ProtocolComponents {

  override def onStart: Session => Session = ProtocolComponents.NoopOnStart

  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit

}
