package tanukki.akka.cluster.autodown.issue22

import akka.cluster.{Member, MultiNodeClusterSpec}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeSpec, STMultiNodeSpec}
import akka.testkit.LongRunningTest

import scala.collection.immutable
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._

class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode1
    extends MultiNodeOldestAutoDownSpec(
      MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false)
    )
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode2
    extends MultiNodeOldestAutoDownSpec(
      MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false)
    )
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode3
    extends MultiNodeOldestAutoDownSpec(
      MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false)
    )
class OldestAutoDowningNodeThatIsUnreachableWithAccrualFailureDetectorMultiJvmNode4
    extends MultiNodeOldestAutoDownSpec(
      MultiNodeOldestAutoDownSpecConfig(failureDetectorPuppet = false)
    )

abstract class MultiNodeOldestAutoDownSpec(
  multiNodeConfig: MultiNodeOldestAutoDownSpecConfig
) extends MultiNodeSpec(multiNodeConfig)
    with STMultiNodeSpec
    with MultiNodeClusterSpec {
  import multiNodeConfig._

  muteMarkingAsUnreachable()

  "The oldest member in a 3 node cluster" must {
    "issue-22" taggedAs LongRunningTest in {
      awaitClusterUp(nodeA, nodeB, nodeC, nodeD)
      val secondAddress = node(nodeB).address
      val thirdAddress = node(nodeC).address
      val fourthAddress = node(nodeD).address

      enterBarrier("before-exit-two-node")
      runOn(nodeA) {
        // kill 'fifth' node
        testConductor.exit(nodeB, 0).await
        testConductor.exit(nodeC, 0).await
        testConductor.exit(nodeD, 0).await
        enterBarrier("down-two-node")

        // mark the node as unreachable in the failure detector
        markNodeAsUnavailable(secondAddress)
        markNodeAsUnavailable(thirdAddress)
        markNodeAsUnavailable(fourthAddress)

        awaitMembersUp(
          numberOfMembers = 1,
          canNotBePartOfMemberRing =
            Set(secondAddress, thirdAddress, fourthAddress),
          30.seconds
        )
      }

      runOn(nodeB, nodeC, nodeD) {
        enterBarrier("down-two-node")
      }

      runOn(nodeA) {
        enterBarrier("down-two-node")

        awaitMembersUp(
          numberOfMembers = 1,
          canNotBePartOfMemberRing =
            Set(secondAddress, thirdAddress, fourthAddress),
          30.seconds
        )
      }
      enterBarrier("await-completion-1")

    }
  }

  def membersByAge: SortedSet[Member] =
    immutable.SortedSet(clusterView.members.toSeq: _*)(Member.ageOrdering)

  def roleByMember(member: Member): RoleName =
    roles.find(r => address(r) == member.address).get

  def isFirst(roleName: RoleName): Boolean = address(roleName) == address(nodeA)

}
