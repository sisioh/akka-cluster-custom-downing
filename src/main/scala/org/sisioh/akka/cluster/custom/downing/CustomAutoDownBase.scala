/**
  * Copyright (C) 2009-2016 Lightbend Inc. <http://www.lightbend.com>
  *
  * 2016- Modified by Yusuke Yasuda
  * 2019- Modified by Junichi Kato
  * The original source code can be found here.
  * https://github.com/akka/akka/blob/master/akka-cluster/src/main/scala/akka/cluster/AutoDown.scala
  */
package org.sisioh.akka.cluster.custom.downing

import akka.actor.{ Actor, Address, Cancellable, Scheduler }
import akka.cluster.ClusterEvent._
import akka.cluster.MemberStatus.{ Down, Exiting }
import akka.cluster._

import scala.concurrent.duration.{ Duration, FiniteDuration }

object CustomDowning {
  private[downing] case class UnreachableTimeout(member: Member)
  private[downing] val skipMemberStatus: Set[MemberStatus] = Set[MemberStatus](Down, Exiting)
}

abstract class CustomAutoDownBase(autoDownUnreachableAfter: FiniteDuration) extends Actor {

  import CustomDowning._

  protected def selfAddress: Address

  protected def down(node: Address): Unit

  protected def downOrAddPending(member: Member): Unit

  protected def downOrAddPendingAll(members: Set[Member]): Unit

  protected def scheduler: Scheduler

  import context.dispatcher

  private var scheduledUnreachable: Map[Member, Cancellable] = Map.empty
  private var pendingUnreachable: Set[Member]                = Set.empty
  private var unstableUnreachable: Set[Member]               = Set.empty

  override def postStop(): Unit = {
    scheduledUnreachable.values foreach { _.cancel }
    super.postStop()
  }

  override def receive: Receive = receiveEvent orElse predefinedReceiveEvent

  protected def receiveEvent: Receive

  private def predefinedReceiveEvent: Receive = {
    case state: CurrentClusterState =>
      initialize(state)
      state.unreachable foreach unreachableMember

    case UnreachableTimeout(member) =>
      if (scheduledUnreachable contains member) {
        scheduledUnreachable -= member
        if (scheduledUnreachable.isEmpty) {
          unstableUnreachable += member
          downOrAddPendingAll(unstableUnreachable)
          unstableUnreachable = Set.empty
        } else {
          unstableUnreachable += member
        }
      }

    case _: ClusterDomainEvent =>
  }

  protected def initialize(state: CurrentClusterState): Unit = {}

  protected def onMemberDowned(member: Member): Unit = {}

  protected def onMemberRemoved(member: Member, previousStatus: MemberStatus): Unit = {}

  protected def onLeaderChanged(leader: Option[Address]): Unit = {}

  protected def onRoleLeaderChanged(role: String, leader: Option[Address]): Unit = {}

  protected def unreachableMember(m: Member): Unit =
    if (!skipMemberStatus(m.status) && !scheduledUnreachable.contains(m))
      scheduleUnreachable(m)

  private def scheduleUnreachable(m: Member): Unit =
    if (autoDownUnreachableAfter == Duration.Zero)
      downOrAddPending(m)
    else {
      val task = scheduler.scheduleOnce(autoDownUnreachableAfter, self, UnreachableTimeout(m))
      scheduledUnreachable += (m -> task)
    }

  protected def remove(member: Member): Unit = {
    scheduledUnreachable.get(member) foreach { _.cancel }
    scheduledUnreachable -= member
    pendingUnreachable -= member
    unstableUnreachable -= member
  }

  protected def scheduledUnreachableMembers: Map[Member, Cancellable] =
    scheduledUnreachable

  protected def pendingUnreachableMembers: Set[Member] = pendingUnreachable

  protected def pendingAsUnreachable(member: Member): Unit = pendingUnreachable += member

  protected def downPendingUnreachableMembers(): Unit = {
    val (head, tail) = pendingUnreachable.splitAt(1)
    head.foreach { member =>
      down(member.address)
    }
    pendingUnreachable = tail
  }

  protected def unstableUnreachableMembers: Set[Member] = unstableUnreachable
}
