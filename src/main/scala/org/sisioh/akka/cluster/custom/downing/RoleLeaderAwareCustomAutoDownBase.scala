/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing

import akka.cluster.ClusterEvent._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class RoleLeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  private var roleLeader: Map[String, Boolean] = Map.empty

  protected def isRoleLeaderOf(role: String): Boolean = roleLeader.getOrElse(role, false)

  override protected def receiveEvent: Receive = {
    case RoleLeaderChanged(role, leaderOption) =>
      roleLeader += (role -> leaderOption.contains(selfAddress))
      if (isRoleLeaderOf(role)) {
        log.info("This node is the new role leader for role {}", role)
      }
      onRoleLeaderChanged(role, leaderOption)
    case UnreachableMember(m) =>
      log.info("{} is unreachable", m)
      unreachableMember(m)
    case ReachableMember(m) =>
      log.info("{} is reachable", m)
      remove(m)
    case MemberRemoved(m, s) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
      onMemberRemoved(m, s)
  }

  override protected def initialize(state: CurrentClusterState): Unit = {
    roleLeader = state.roleLeaderMap.mapValues(_.exists(_ == selfAddress)).toMap
    super.initialize(state)
  }

}
