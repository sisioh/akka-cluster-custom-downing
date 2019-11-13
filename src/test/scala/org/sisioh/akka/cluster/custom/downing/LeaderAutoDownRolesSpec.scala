/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  * 2016- Modified by Yusuke Yasuda
  *
  * original source code is from
  * https://github.com/akka/akka/blob/master/akka-cluster/src/test/scala/akka/cluster/AutoDownSpec.scala
  */

package org.sisioh.akka.cluster.custom.downing

import akka.actor._
import akka.cluster.ClusterEvent.{ LeaderChanged, MemberRemoved, ReachableMember, UnreachableMember }
import akka.cluster.MemberStatus.{ Down, Exiting, Removed, Up }
import akka.cluster.TestMember

import scala.concurrent.duration.{ FiniteDuration, _ }

case class DownCalled(node: Address)

object LeaderAutoDownRolesSpec {

  val memberRoles = Set("testRole", "dc-1")
  val testRoles   = Set("testRole")

  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, memberRoles)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, memberRoles)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, memberRoles)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, Set("otherRole", "dc-1"))

  class LeaderAutoDownRolesTestActor(
      targetRoles: Set[String],
      autoDownUnreachableAfter: FiniteDuration,
      probe: ActorRef
  ) extends LeaderAutoDownRolesBase(targetRoles, autoDownUnreachableAfter) {

    override protected def selfAddress: Address = memberA.address
    override protected def scheduler: Scheduler = context.system.scheduler

    override protected def down(node: Address): Unit = {
      if (isLeader)
        probe ! DownCalled(node)
      else
        probe ! "down must only be done by leader"
    }

  }
}

class LeaderAutoDownRolesSpec extends AkkaSpec(ActorSystem("LeaderAutoDownRolesSpec")) {
  import LeaderAutoDownRolesSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(Props(new LeaderAutoDownRolesTestActor(testRoles, autoDownUnreachableAfter, testActor)))

  "LeaderAutoDownRoles" must {

    "down unreachable when leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectMsg(DownCalled(memberB.address))
    }

    "not down unreachable when not leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      expectNoMessage(1.second)
    }

    "down unreachable when becoming leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberA.address))
      expectMsg(DownCalled(memberC.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberB.address))
    }

    "down unreachable when becoming leader inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberA.address))
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberC.address))
    }

    "not down unreachable when losing leadership inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberC)
      a ! LeaderChanged(Some(memberB.address))
      expectNoMessage(3.second)
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMessage(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMessage(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMessage(1.second)
    }

    /*-------------------------------------------------------------------*/

    "not down unreachable with different role" in {
      val a = autoDownActor(Duration.Zero)
      a ! LeaderChanged(Some(memberA.address))
      a ! UnreachableMember(memberD)
      expectNoMessage(1.second)
    }
  }
}
