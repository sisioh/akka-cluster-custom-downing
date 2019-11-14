/**
  * Copyright (C) 2016- Yuske Yasuda
  * Copyright (C) 2019- SISIOH Project
  */
package org.sisioh.akka.cluster.custom.downing

import akka.cluster.ClusterEvent._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class LeaderAwareCustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  private var leader: Boolean = false

  protected def isLeader: Boolean = leader

  override protected def receiveEvent: Receive = {
    case LeaderChanged(leaderOption) =>
      leader = leaderOption.contains(selfAddress)
      if (isLeader) {
        log.info("This node is the new Leader")
      }
      onLeaderChanged(leaderOption)
    case UnreachableMember(m) =>
      log.info("{} is unreachable", m)
      unreachableMember(m)
    case ReachableMember(m) =>
      log.info("{} is reachable", m)
      remove(m)
    case MemberDowned(m) =>
      log.info("{} was downed", m)
      onMemberDowned(m)
    case MemberRemoved(m, s) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
      onMemberRemoved(m, s)
  }

  override protected def initialize(state: CurrentClusterState): Unit = {
    leader = state.leader.contains(selfAddress)
    super.initialize(state)
  }
}
