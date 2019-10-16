package tanukki.akka.cluster.autodown

import akka.actor.Address
import akka.cluster.MemberStatus.Down
import akka.cluster.{MemberStatus, Member}

import scala.concurrent.duration.FiniteDuration

abstract class MajorityLeaderAutoDownBase(
  majorityMemberRole: Option[String],
  downIfInMinority: Boolean,
  autoDownUnreachableAfter: FiniteDuration
) extends MajorityAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  override protected def onLeaderChanged(leader: Option[Address]): Unit = {
    if (majorityMemberRole.isEmpty && isLeader) downPendingUnreachableMembers()
  }

  override protected def onRoleLeaderChanged(role: String,
                                             leader: Option[Address]): Unit = {
    majorityMemberRole.foreach { r =>
      if (r == role && isRoleLeaderOf(r)) downPendingUnreachableMembers()
    }
  }

  override protected def onMemberRemoved(member: Member,
                                         previousStatus: MemberStatus): Unit = {
    if (isMajority(majorityMemberRole)) {
      if (isLeaderOf(majorityMemberRole)) {
        downPendingUnreachableMembers()
      }
    } else {
      down(selfAddress)
    }
    super.onMemberRemoved(member, previousStatus)
  }

  override protected def downOrAddPending(member: Member): Unit = {
    if (isLeaderOf(majorityMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    } else {
      pendingAsUnreachable(member)
    }
  }

  override protected def downOrAddPendingAll(members: Set[Member]): Unit = {
    if (isMajorityAfterDown(members, majorityMemberRole)) {
      members.foreach(downOrAddPending)
    } else if (downIfInMinority) {
      shutdownSelf()
    }
  }
}
