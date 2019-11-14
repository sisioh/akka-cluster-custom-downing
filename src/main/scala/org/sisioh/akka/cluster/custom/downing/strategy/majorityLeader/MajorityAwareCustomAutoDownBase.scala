/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  *
  * 2016- Modified by Yusuke Yasuda
  * 2019- Modified by Junichi Kato
  * The original source code can be found here.
  * https://github.com/akka/akka/blob/master/akka-cluster/src/main/scala/akka/cluster/AutoDown.scala
  */
package org.sisioh.akka.cluster.custom.downing.strategy.majorityLeader

import akka.cluster.ClusterEvent._
import akka.cluster.{ Member, MemberStatus }
import akka.event.Logging
import org.sisioh.akka.cluster.custom.downing.SplitBrainResolver
import org.sisioh.akka.cluster.custom.downing.strategy.{ CustomAutoDownBase, Members }

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration

abstract class MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter)
    with SplitBrainResolver {

  private val log = Logging(context.system, this)

  private var leader: Boolean                               = false
  private var roleLeader: Map[String, Boolean]              = Map.empty
  private var membersByAddress: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ordering)

  override protected def receiveEvent: Receive = {
    case LeaderChanged(leaderOption) =>
      leader = leaderOption.contains(selfAddress)
      if (isLeader) {
        log.info("This node is the new Leader")
      }
      onLeaderChanged(leaderOption)
    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader += (role -> leaderOption.contains(selfAddress))
      if (isRoleLeaderOf(role)) {
        log.info("This node is the new role leader for role {}", role)
      }
      onRoleLeaderChanged(role, leaderOption)
    case MemberUp(m) =>
      log.info("{} is up", m)
      replaceMember(m)
    case UnreachableMember(m) =>
      log.info("{} is unreachable", m)
      replaceMember(m)
      unreachableMember(m)
    case ReachableMember(m) =>
      log.info("{} is reachable", m)
      replaceMember(m)
      remove(m)
    case MemberLeft(m) =>
      log.info("{} left the cluster", m)
      replaceMember(m)
    case MemberExited(m) =>
      log.info("{} exited the cluster", m)
      replaceMember(m)
    case MemberRemoved(m, prev) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
      removeMember(m)
      onMemberRemoved(m, prev)
  }

  protected def isLeader: Boolean = leader

  protected def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  override protected def initialize(state: CurrentClusterState): Unit = {
    leader = state.leader.contains(selfAddress)
    roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress)).toMap
    membersByAddress = immutable.SortedSet.empty(Member.ordering) union state.members.filterNot { m =>
        m.status == MemberStatus.Removed
      }
    super.initialize(state)
  }

  protected def replaceMember(member: Member): Unit = {
    membersByAddress -= member
    membersByAddress += member
  }

  protected def removeMember(member: Member): Unit = {
    membersByAddress -= member
  }

  protected def isLeaderOf(majorityRole: Option[String]): Boolean = majorityRole.fold(isLeader)(isRoleLeaderOf)

  protected def majorityMemberOf(role: Option[String]): SortedSet[Member] = {
    val ms = membersByAddress
    role.fold(ms)(r => ms.filter(_.hasRole(r)))
  }

  protected def isMajority(role: Option[String]): Boolean = {
    val ms        = majorityMemberOf(role)
    val okMembers = ms filter isOK
    val koMembers = ms -- okMembers

    val isEqual = okMembers.size == koMembers.size
    okMembers.size > koMembers.size || isEqual && ms.headOption.map(okMembers.contains(_)).getOrElse(true)
  }

  protected def isMajorityAfterDown(members: Members, role: Option[String]): Boolean = {
    val minus =
      if (role.isEmpty) members
      else {
        val r = role.get
        members.filter(_.hasRole(r))
      }
    val ms        = majorityMemberOf(role)
    val okMembers = (ms filter isOK) -- minus.toSet
    val koMembers = ms -- okMembers

    val isEqual = okMembers.size == koMembers.size
    okMembers.size > koMembers.size || isEqual && ms.headOption.map(okMembers.contains(_)).getOrElse(true)
  }

  private def isOK(member: Member) = {
    (member.status == MemberStatus.Up || member.status == MemberStatus.Leaving) &&
    (!pendingUnreachableMembers.contains(member) && !unstableUnreachableMembers.contains(member))
  }

  private def isKO(member: Member): Boolean = !isOK(member)
}
