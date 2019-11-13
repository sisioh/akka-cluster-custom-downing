package org.sisioh.akka.cluster.custom.downing

trait SplitBrainResolver {
  def shutdownSelf(): Unit
}
