/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing.strategy.oldest

import akka.cluster.ClusterEvent._
import akka.cluster.{ Member, MemberStatus }
import akka.event.Logging
import org.sisioh.akka.cluster.custom.downing.SplitBrainResolver
import org.sisioh.akka.cluster.custom.downing.strategy.CustomAutoDownBase

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration.FiniteDuration

abstract class OldestAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter)
    with SplitBrainResolver {

  private val log = Logging(context.system, this)

  private var membersByAge: immutable.SortedSet[Member] =
    immutable.SortedSet.empty(Member.ageOrdering)

  override protected def receiveEvent: Receive = {
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
      log.info("{} is left the cluster", m)
      replaceMember(m)
    case MemberExited(m) =>
      log.info("{} exited the cluster", m)
      replaceMember(m)
    case MemberDowned(m) =>
      log.info("{} was downed", m)
      replaceMember(m)
      onMemberDowned(m)
    case MemberRemoved(m, prev) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
      removeMember(m)
      onMemberRemoved(m, prev)
  }

  override protected def initialize(state: CurrentClusterState): Unit = {
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

  protected def isAllIntermediateMemberRemoved(member: Member): Boolean = {
    val isUnsafe = membersByAge.filterNot(_ == member).exists { m =>
      m.status == MemberStatus.Down || m.status == MemberStatus.Exiting
    }
    !isUnsafe
  }

  protected def isAllIntermediateMemberRemoved: Boolean = {
    val isUnsafe = membersByAge.exists { m =>
      m.status == MemberStatus.Down || m.status == MemberStatus.Exiting
    }
    !isUnsafe
  }

  protected def isOldestUnsafe(role: Option[String]): Boolean = {
    targetMembers(role).headOption.map(_.address).contains(selfAddress)
  }

  protected def isOldest: Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(None)
  }

  protected def isOldestWithout(member: Member): Boolean = {
    isAllIntermediateMemberRemoved(member) && isOldestUnsafe(None)
  }

  protected def isOldestOf(role: Option[String]): Boolean = {
    isAllIntermediateMemberRemoved && isOldestUnsafe(role)
  }

  protected def isOldestOf(role: Option[String], without: Member): Boolean = {
    isAllIntermediateMemberRemoved(without) && isOldestUnsafe(role)
  }

  protected def isOldestAlone(role: Option[String]): Boolean = {
    val tm = targetMembers(role)
    if (tm.isEmpty || tm.size == 1) true
    else {
      val oldest = tm.head
      val rest   = tm.tail
      if (isOldestUnsafe(role)) {
        isOK(oldest) && rest.forall(isKO)
      } else {
        isKO(oldest) && rest.forall(isOK)
      }
    }
  }

  protected def isSecondaryOldest(role: Option[String]): Boolean = {
    val tm = targetMembers(role)
    if (tm.size >= 2) {
      tm.slice(1, 2).head.address == selfAddress
    } else false
  }

  protected def oldestMember(role: Option[String]): Option[Member] =
    targetMembers(role).headOption

  private def targetMembers(role: Option[String]): SortedSet[Member] = {
    role.fold(membersByAge)(r => membersByAge.filter(_.hasRole(r)))
  }

  private def isOK(member: Member) = {
    (member.status == MemberStatus.Up || member.status == MemberStatus.Leaving) &&
    (!pendingUnreachableMembers.contains(member) && !unstableUnreachableMembers
      .contains(member))
  }

  private def isKO(member: Member): Boolean = !isOK(member)
}
