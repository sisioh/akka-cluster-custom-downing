/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  *
  * 2016- Modified by Yusuke Yasuda
  * The original source code can be found here.
  * https://github.com/akka/akka/blob/master/akka-cluster/src/main/scala/akka/cluster/AutoDown.scala
  */
package tanukki.akka.cluster.autodown

import akka.actor.{Actor, Address, Cancellable, Scheduler}
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{Down, Exiting}
import akka.cluster._
import akka.event.Logging

import scala.concurrent.duration.{Duration, FiniteDuration}

object CustomDowning {
  case class UnreachableTimeout(member: Member)
}

abstract class CustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration)
    extends Actor {

  private val log = Logging(context.system, this)

  import CustomDowning._

  protected def selfAddress: Address

  protected def down(node: Address): Unit

  protected def downOrAddPending(member: Member): Unit

  protected def downOrAddPendingAll(members: Set[Member]): Unit

  protected def scheduler: Scheduler

  import context.dispatcher

  private val skipMemberStatus: Set[MemberStatus] = Set(Down, Exiting)

  private var scheduledUnreachable: Map[Member, Cancellable] = Map.empty
  private var pendingUnreachable: Set[Member] = Set.empty
  private var unstableUnreachable: Set[Member] = Set.empty

  override def postStop(): Unit = {
    scheduledUnreachable.values foreach { _.cancel }
    super.postStop()
  }

  override def unhandled(message: Any): Unit = {
    log.debug("unhandled message: {}", message)
    super.unhandled(message)
  }

  override def receive: Receive = receiveEvent orElse predefinedReceiveEvent

  protected def receiveEvent: Receive

  protected def predefinedReceiveEvent: Receive = {
    case state: CurrentClusterState =>
      log.debug(s"CurrentClusterState: $state")
      initialize(state)
      state.unreachable foreach unreachableMember

    case UnreachableTimeout(member) =>
      log.debug(s"Unreachable timeout: $member")
      if (scheduledUnreachable contains member) {
        log.debug(s"remove unstable unreachable member: $member")
        scheduledUnreachable -= member
        if (scheduledUnreachable.isEmpty) {
          log.debug(s"downOrAddPendingAll: $member")
          unstableUnreachable += member
          downOrAddPendingAll(unstableUnreachable)
          unstableUnreachable = Set.empty
        } else {
          log.debug(s"append unstable unreachable member: $member")
          unstableUnreachable += member
        }
      }

    case _: ClusterDomainEvent =>
  }

  protected def initialize(state: CurrentClusterState): Unit = {}

  protected def unreachableMember(m: Member): Unit = {
    log.debug(s"unreachableMember: $m")
    if (!skipMemberStatus(m.status) && !scheduledUnreachable.contains(m))
      scheduleUnreachable(m)
  }

  protected def scheduleUnreachable(m: Member): Unit = {
    log.debug(s"scheduleUnreachable: $m")
    if (autoDownUnreachableAfter == Duration.Zero) {
      log.debug(s"autoDownUnreachableAfter == Zero : $m")
      downOrAddPending(m)
    } else {
      log.debug(s"autoDownUnreachableAfter != Zero : $m")
      val task = scheduler.scheduleOnce(
        autoDownUnreachableAfter,
        self,
        UnreachableTimeout(m)
      )
      scheduledUnreachable += (m -> task)
    }
  }

  protected def remove(member: Member): Unit = {
    log.debug(s"remove: $member")
    scheduledUnreachable.get(member) foreach { _.cancel }
    scheduledUnreachable -= member
    pendingUnreachable -= member
    unstableUnreachable -= member
  }

  protected def scheduledUnreachableMembers: Map[Member, Cancellable] = {
    log.debug("Get scheduledUnreachableMembers")
    scheduledUnreachable
  }

  protected def pendingUnreachableMembers: Set[Member] = {
    log.debug("Get pendingUnreachableMembers")
    pendingUnreachable
  }

  protected def pendingAsUnreachable(member: Member): Unit = {
    log.debug(s"pendingAsUnreachable: $member")
    pendingUnreachable += member
  }

  protected def downPendingUnreachableMembers(): Unit = {
    log.debug("downPendingUnreachableMembers")
    val (head, tail) = pendingUnreachable.splitAt(1)
    head.foreach { member =>
      log.debug(s"---> down($member)")
      down(member.address)
    }
    pendingUnreachable = tail
  }

  protected def unstableUnreachableMembers: Set[Member] = {
    log.debug("Get unstableUnreachableMembers")
    unstableUnreachable
  }
}
