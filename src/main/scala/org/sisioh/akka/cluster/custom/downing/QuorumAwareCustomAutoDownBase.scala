/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing

import akka.cluster.ClusterEvent._
import akka.cluster.{ Member, MemberStatus }
import akka.event.Logging

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration

abstract class QuorumAwareCustomAutoDownBase(quorumSize: Int, autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter)
    with SplitBrainResolver {

  private val log = Logging(context.system, this)

  private var leader: Boolean                  = false
  private var roleLeader: Map[String, Boolean] = Map.empty

  private var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(Member.ageOrdering)

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
      log.info("{} exited from the cluster", m)
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
    membersByAge = immutable.SortedSet.empty(Member.ageOrdering) union state.members.filterNot { m =>
        m.status == MemberStatus.Removed
      }
    super.initialize(state)
  }

  protected def replaceMember(member: Member): Unit = {
    membersByAge -= member
    membersByAge += member
  }

  protected def removeMember(member: Member): Unit = {
    membersByAge -= member
  }

  protected def isLeaderOf(quorumRole: Option[String]): Boolean = quorumRole.fold(isLeader)(isRoleLeaderOf)

  def targetMember: SortedSet[Member] = membersByAge.filter { m =>
    (m.status == MemberStatus.Up || m.status == MemberStatus.Leaving) &&
    !pendingUnreachableMembers.contains(m)
  }

  def quorumMemberOf(role: Option[String]): SortedSet[Member] = {
    val ms = targetMember
    role.fold(ms)(r => ms.filter(_.hasRole(r)))
  }

  def isQuorumMet(role: Option[String]): Boolean = {
    val ms = quorumMemberOf(role)
    ms.size >= quorumSize
  }

  def isQuorumMetAfterDown(members: Set[Member], role: Option[String]): Boolean = {
    val minus =
      if (role.isEmpty) members.size
      else {
        val r = role.get
        members.count(_.hasRole(r))
      }
    val ms = quorumMemberOf(role)
    ms.size - minus >= quorumSize
  }

  def isUnreachableStable: Boolean = scheduledUnreachableMembers.isEmpty

}
