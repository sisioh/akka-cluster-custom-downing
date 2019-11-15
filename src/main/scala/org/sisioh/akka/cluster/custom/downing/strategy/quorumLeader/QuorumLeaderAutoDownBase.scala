/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing.strategy.quorumLeader

import akka.actor.Address
import akka.cluster.MemberStatus.Down
import akka.cluster.{ Member, MemberStatus }
import org.sisioh.akka.cluster.custom.downing.strategy.Members

import scala.concurrent.duration.FiniteDuration

abstract class QuorumLeaderAutoDownBase(
    quorumRole: Option[String],
    quorumSize: Int,
    downIfOutOfQuorum: Boolean,
    autoDownUnreachableAfter: FiniteDuration
) extends QuorumAwareCustomAutoDownBase(quorumSize, autoDownUnreachableAfter) {

  override protected def onLeaderChanged(leader: Option[Address]): Unit = {
    if (quorumRole.isEmpty && isLeader)
      downPendingUnreachableMembers()
  }

  override protected def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {
    quorumRole.foreach { r =>
      if (r == role && isRoleLeaderOf(r))
        downPendingUnreachableMembers()
    }
  }

  override protected def onMemberDowned(member: Member): Unit = {
    if (isQuorumMet(quorumRole)) {
      if (isLeaderOf(quorumRole)) {
        downPendingUnreachableMembers()
      }
    } else
      down(selfAddress)
  }

  override protected def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isQuorumMet(quorumRole)) {
      if (isLeaderOf(quorumRole)) {
        downPendingUnreachableMembers()
      }
    } else
      down(selfAddress)
  }

  override protected def downOrAddPending(member: Member): Unit = {
    if (isLeaderOf(quorumRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else
      addPendingUnreachableMember(member)
  }

  override protected def downOrAddPendingAll(members: Members): Unit = {
    if (isQuorumMetAfterDown(members, quorumRole)) {
      members.foreach(downOrAddPending)
    } else if (downIfOutOfQuorum) {
      shutdownSelf()
    }
  }
}
