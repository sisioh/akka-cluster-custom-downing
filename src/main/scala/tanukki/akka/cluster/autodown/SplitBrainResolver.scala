package tanukki.akka.cluster.autodown

trait SplitBrainResolver {
  protected def shutdownSelf(): Unit
}
