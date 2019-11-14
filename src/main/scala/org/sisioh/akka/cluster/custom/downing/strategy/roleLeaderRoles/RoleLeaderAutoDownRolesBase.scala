/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing.strategy.roleLeaderRoles

import akka.actor.Address
import akka.cluster.Member

import scala.concurrent.duration.FiniteDuration

abstract class RoleLeaderAutoDownRolesBase(
    leaderRole: String,
    targetRoles: Set[String],
    autoDownUnreachableAfter: FiniteDuration
) extends RoleLeaderAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  override protected def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit =
    if (leaderRole == role && isRoleLeaderOf(leaderRole))
      downPendingUnreachableMembers()

  override protected def downOrAddPending(member: Member): Unit = {
    if (targetRoles.exists(role => member.hasRole(role))) {
      if (isRoleLeaderOf(leaderRole))
        down(member.address)
      else
        pendingAsUnreachable(member)
    }
  }

  override protected def downOrAddPendingAll(members: Set[Member]): Unit =
    members.foreach(downOrAddPending)

}
