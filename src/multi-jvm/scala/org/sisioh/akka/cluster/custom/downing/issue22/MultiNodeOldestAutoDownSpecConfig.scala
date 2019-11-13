package org.sisioh.akka.cluster.custom.downing.issue22

import akka.cluster.MultiNodeClusterSpec
import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

final case class MultiNodeOldestAutoDownSpecConfig(
    failureDetectorPuppet: Boolean
) extends MultiNodeConfig {
  val nodeA = role("master")
  val nodeB = role("nodeB")
  val nodeC = role("nodeC")

  commonConfig(
    ConfigFactory
      .parseString("""
          |akka.cluster.downing-provider-class = "org.sisioh.akka.cluster.custom.downing.OldestAutoDowning"
          |custom-downing {
          |  stable-after = 1s
          |
          |  oldest-auto-downing {
          |    oldest-member-role = "master"
          |    down-if-alone = false
          |  }
          |}
          |akka.cluster.metrics.enabled=off
          |akka.actor.warn-about-java-serializer-usage = off
          |akka.remote.log-remote-lifecycle-events = off
    """.stripMargin)
      .withFallback(MultiNodeClusterSpec.clusterConfig(failureDetectorPuppet))
  )

  nodeConfig(nodeA)(ConfigFactory.parseString("""
                                                |akka.cluster {
                                                |  roles = ["master"]
                                                |}
    """.stripMargin))

  nodeConfig(nodeB, nodeC)(ConfigFactory.parseString("""
                                                              |akka.cluster {
                                                              |  roles = [role]
                                                              |}
    """.stripMargin))

}
