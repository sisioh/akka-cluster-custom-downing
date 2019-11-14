/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing

import akka.actor.Address
import akka.cluster.Member

import scala.concurrent.duration.FiniteDuration

abstract class LeaderAutoDownRolesBase(targetRoles: Set[String], autoDownUnreachableAfter: FiniteDuration)
    extends LeaderAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  override def onLeaderChanged(leader: Option[Address]): Unit =
    if (isLeader) downPendingUnreachableMembers()

  override protected def downOrAddPending(member: Member): Unit =
    if (targetRoles.exists(role => member.hasRole(role))) {
      if (isLeader)
        down(member.address)
      else
        pendingAsUnreachable(member)
    }

  override protected def downOrAddPendingAll(members: Set[Member]): Unit =
    members.foreach(downOrAddPending)

}
