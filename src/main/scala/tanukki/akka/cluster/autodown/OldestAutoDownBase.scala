package tanukki.akka.cluster.autodown

import akka.cluster.MemberStatus.Down
import akka.cluster.{Member, MemberStatus}
import akka.event.Logging

import scala.concurrent.duration.FiniteDuration

abstract class OldestAutoDownBase(oldestMemberRole: Option[String],
                                  downIfAlone: Boolean,
                                  autoDownUnreachableAfter: FiniteDuration)
    extends OldestAwareCustomAutoDownBase(autoDownUnreachableAfter) {

  private val log = Logging(context.system, this)

  override def onMemberDowned(member: Member): Unit = {
    log.info(s"onMemberDowned:start $member")
    if (isAllIntermediateMemberRemovedOnlyExiting && isOldestUnsafe(
          oldestMemberRole
        ))
      downPendingUnreachableMembers()
    log.info(s"onMemberDowned:finished $member")
  }

  override protected def onMemberRemoved(member: Member,
                                         previousStatus: MemberStatus): Unit = {
    log.info(s"onMemberRemoved:start $member, $previousStatus")
    if (isAllIntermediateMemberRemoved && isOldestUnsafe(oldestMemberRole))
      downPendingUnreachableMembers()
    log.info(s"onMemberRemoved:finish $member, $previousStatus")
  }

  override protected def downOrAddPending(member: Member): Unit = {
    log.debug(s"downOrAddPending:start $member, $oldestMemberRole")
    if (isOldestOf(oldestMemberRole)) {
      log.debug(s"isOldestOf = true :$member")
      log.debug(s"---> down(${member.address})")
      down(member.address)
      replaceMember(member.copy(Down))
    } else {
      log.debug(s"isOldestOf = false :$member")
      log.debug(s"+++> down pending")
      pendingAsUnreachable(member)
    }
    log.debug(s"downOrAddPending:finish $member")
  }

  protected def downOnSecondary(member: Member): Unit = {
    log.debug(s"downOnSecondary:start $member")
    if (isSecondaryOldest(oldestMemberRole)) {
      down(member.address)
      replaceMember(member.copy(Down))
    }
    log.debug(s"downOnSecondary:finish $member")
  }

  override protected def downOrAddPendingAll(members: Set[Member]): Unit = {
    log.debug(s"downOrAddPendingAll:start $members")
    val oldest = oldestMember(oldestMemberRole)
    if (downIfAlone && isOldestAlone(oldestMemberRole)) {
      if (isOldestOf(oldestMemberRole)) {
        shutdownSelf()
      } else if (isSecondaryOldest(oldestMemberRole)) {
        members.foreach(downOnSecondary)
      } else {
        members.foreach(downOrAddPending)
      }
    } else {
      if (oldest.fold(true)(o => members.contains(o))) {
        shutdownSelf()
      } else {
        members.foreach(downOrAddPending)
      }
    }
    log.debug(s"downOrAddPendingAll:finish $members")
  }

  protected def downAloneOldest(member: Member): Unit = {
    log.debug(s"downAloneOldest:start $member")
    val oldestOpt = oldestMember(oldestMemberRole)
    if (isOldestOf(oldestMemberRole)) {
      shutdownSelf()
    } else if (isSecondaryOldest(oldestMemberRole) && oldestOpt.contains(
                 member
               )) {
      oldestOpt.foreach { oldest =>
        log.debug(s"---> down($oldest)")
        down(oldest.address)
        replaceMember(oldest.copy(Down))
      }
    } else {
      pendingAsUnreachable(member)
    }
    log.debug(s"downAloneOldest:finish $member")
  }
}
