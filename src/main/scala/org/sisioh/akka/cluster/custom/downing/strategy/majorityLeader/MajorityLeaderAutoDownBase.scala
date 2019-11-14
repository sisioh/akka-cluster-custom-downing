/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing.strategy.majorityLeader

import akka.actor.Address
import akka.cluster.MemberStatus.Down
import akka.cluster.{ Member, MemberStatus }
import org.sisioh.akka.cluster.custom.downing.strategy.Members

import scala.concurrent.duration.FiniteDuration

abstract class MajorityLeaderAutoDownBase(
    majorityMemberRole: Option[String],
    downIfInMinority: Boolean,
    autoDownUnreachableAfter: FiniteDuration
) extends MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  override protected def onLeaderChanged(leader: Option[Address]): Unit = {
    if (majorityMemberRole.isEmpty && isLeader) downPendingUnreachableMembers()
  }

  override protected def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {
    majorityMemberRole.foreach { r =>
      if (r == role && isRoleLeaderOf(r)) downPendingUnreachableMembers()
    }
  }

  override protected def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {
    if (isMajority(majorityMemberRole)) {
      if (isLeaderOf(majorityMemberRole)) {
        downPendingUnreachableMembers()
      }
    } else {
      down(selfAddress)
    }
    super.onMemberRemoved(member, previousStatus)
  }

  override protected def downOrAddPending(member: Member): Unit =
    if (isLeaderOf(majorityMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else
      pendingAsUnreachable(member)

  override protected def downOrAddPendingAll(members: Members): Unit = {
    if (isMajorityAfterDown(members, majorityMemberRole)) {
      members.foreach(downOrAddPending)
    } else if (downIfInMinority) {
      shutdownSelf()
    }
  }
}
