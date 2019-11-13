package org.sisioh.akka.cluster.custom.downing

trait SplitBrainResolver {
  protected def shutdownSelf(): Unit
}
