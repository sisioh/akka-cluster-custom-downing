package org.sisioh.akka.cluster.custom.downing

import akka.actor.ActorSystem
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class AkkaSpec(system: ActorSystem) extends TestKit(system) with AnyWordSpecLike with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }
}
