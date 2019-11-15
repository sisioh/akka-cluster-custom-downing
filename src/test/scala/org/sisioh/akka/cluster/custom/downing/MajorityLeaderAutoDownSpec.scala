/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  * 2016- Modified by Yusuke Yasuda
  *
  * original source code is from
  * https://github.com/akka/akka/blob/master/akka-cluster/src/test/scala/akka/cluster/AutoDownSpec.scala
  */

package org.sisioh.akka.cluster.custom.downing

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus._
import akka.cluster.{ Member, TestMember }
import org.sisioh.akka.cluster.custom.downing.OldestAutoDownRolesSpec.initialMembersByAge
import org.sisioh.akka.cluster.custom.downing.strategy.majorityLeader.MajorityLeaderAutoDownBase

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.{ Duration, FiniteDuration, _ }

object MajorityLeaderAutoDownSpec {
  val testRole           = Set("testRole", "dc-1")
  val leaderRole: String = testRole.head

  val memberA = TestMember(Address("akka.tcp", "sys", "a", 2552), Up, testRole)
  val memberB = TestMember(Address("akka.tcp", "sys", "b", 2552), Up, testRole)
  val memberC = TestMember(Address("akka.tcp", "sys", "c", 2552), Up, testRole)
  val memberD = TestMember(Address("akka.tcp", "sys", "d", 2552), Up, testRole)
  val memberE = TestMember(Address("akka.tcp", "sys", "e", 2552), Up, testRole)

  val initialMembersByAddress: SortedSet[Member] =
    immutable.SortedSet(memberA, memberB, memberC, memberD, memberE)(Member.ordering)

  class MajorityLeaderAutoDownTestActor(
      address: Address,
      majorityRole: Option[String],
      autoDownUnreachableAfter: FiniteDuration,
      probe: ActorRef
  ) extends MajorityLeaderAutoDownBase(
        majorityRole,
        downIfInMinority = true,
        autoDownUnreachableAfter = autoDownUnreachableAfter
      ) {

    override protected def selfAddress: Address = address
    override protected def scheduler: Scheduler = context.system.scheduler

    override protected def down(node: Address): Unit = {
      val member = initialMembersByAge.find(_.address == node).get
      if (isMajority(majorityRole)) {
        if (majorityRole.fold(isLeader)(isRoleLeaderOf)) {
          probe ! DownCalled(node)
          self ! MemberDowned(member.copy(Down))
        } else {
          probe ! "down must only be done by quorum leader"
        }
      } else
        shutdownSelf()
    }

    override def shutdownSelf(): Unit = {
      probe ! ShutDownCausedBySplitBrainResolver
    }

  }
}

class MajorityLeaderAutoDownSpec extends AkkaSpec(ActorSystem("OldestAutoDownRolesSpec")) {
  import MajorityLeaderAutoDownSpec._

  def autoDownActor(autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(
      Props(new MajorityLeaderAutoDownTestActor(memberA.address, Some(leaderRole), autoDownUnreachableAfter, testActor))
    )

  def autoDownActorOf(address: Address, autoDownUnreachableAfter: FiniteDuration): ActorRef =
    system.actorOf(
      Props(new MajorityLeaderAutoDownTestActor(address, Some(leaderRole), autoDownUnreachableAfter, testActor))
    )

  "MajorityLeaderAutoDown" must {

    "down unreachable when role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectMsg(DownCalled(memberB.address))
    }

    "not down unreachable when not role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      expectNoMessage(1.second)
    }

    "down unreachable when becoming role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectMsg(DownCalled(memberC.address))
    }

    "2-down unreachable when becoming role leader" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! UnreachableMember(memberD)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectMsg(DownCalled(memberC.address))
      expectMsg(DownCalled(memberD.address))
    }

    "down unreachable after specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberB.address))
    }

    "down unreachable when becoming role leader inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectNoMessage(1.second)
      expectMsg(DownCalled(memberC.address))
    }

    "not down unreachable when losing role leadership inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberC)
      a ! RoleLeaderChanged(leaderRole, Some(memberB.address))
      expectNoMessage(3.second)
    }

    "not down when unreachable become reachable inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! ReachableMember(memberB)
      expectNoMessage(3.second)
    }

    "not down when unreachable is removed inbetween detection and specified duration" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! MemberRemoved(memberB.copy(Removed), previousStatus = Exiting)
      expectNoMessage(3.second)
    }

    "not down when unreachable is already Down" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB.copy(Down))
      expectNoMessage(1.second)
    }

    /*-------------------------------------------------------------------*/

    "down unreachable when part of majority even if member removed" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! MemberRemoved(memberB.copy(Removed), Down)
      a ! UnreachableMember(memberC)
      expectMsg(DownCalled(memberC.address))
    }

    "not down self when removing member" in {
      val a = autoDownActor(Duration.Zero)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! MemberRemoved(memberB.copy(Removed), Down)
      a ! MemberRemoved(memberC.copy(Removed), Down)
      a ! MemberRemoved(memberD.copy(Removed), Down)
      expectNoMessage(3.second)
    }

    "down unreachable when in majority if members are unreachable" in {
      val a = autoDownActor(3.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      a ! UnreachableMember(memberB)
      a ! UnreachableMember(memberC)
      expectNoMessage(2.second)
      expectMsgAllOf(DownCalled(memberB.address), DownCalled(memberC.address))
    }

    "down self when quorum is NOT met via unreachable" in {
      val a = autoDownActor(2.seconds)
      a ! CurrentClusterState(members = initialMembersByAddress)
      a ! UnreachableMember(memberB)
      a ! UnreachableMember(memberC)
      Thread.sleep(500)
      a ! UnreachableMember(memberD)
      a ! RoleLeaderChanged(leaderRole, Some(memberA.address))
      expectMsgAllOf(ShutDownCausedBySplitBrainResolver)
    }
  }
}
