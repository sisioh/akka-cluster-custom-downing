package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.ClusterEvent._
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class LeaderAwareCustomAutoDownBase(
    autoDownUnreachableAfter: FiniteDuration)
    extends CustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  private var leader = false

  protected def onLeaderChanged(leader: Option[Address]): Unit = {}

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
    case MemberRemoved(m, _) =>
      log.info("{} was removed from the cluster", m)
      remove(m)
  }

  override protected def initialize(state: CurrentClusterState): Unit = {
    leader = state.leader.contains(selfAddress)
    super.initialize(state)
  }
}
