package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.{Cluster, DowningProvider}

import scala.concurrent.Await
import scala.concurrent.duration._

class OldestAutoDowning(system: ActorSystem) extends DowningProvider {

  private[this] val cluster = Cluster(system)

  private val configObject = system.settings.config.getConfig("custom-downing")
  private val stableAfter =
    configObject.getDuration("stable-after").toMillis millis

  override def downRemovalMargin: FiniteDuration = {
    val key = "down-removal-margin"
    configObject.getString(key) match {
      case "off" => Duration.Zero
      case _     => configObject.getDuration(key).toMillis millis
    }
  }

  override def downingActorProps: Option[Props] = {
    val oldestMemberRole = {
      val r = configObject.getString("oldest-auto-downing.oldest-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val downIfAlone =
      configObject.getBoolean("oldest-auto-downing.down-if-alone")
    val shutdownActorSystem = configObject.getBoolean(
      "oldest-auto-downing.shutdown-actor-system-on-resolution"
    )
    if (stableAfter == Duration.Zero && downIfAlone)
      throw new ConfigurationException(
        "If you set down-if-alone=true, stable-after timeout must be greater than zero."
      )
    else
      Some(
        OldestAutoDown.props(
          oldestMemberRole,
          downIfAlone,
          shutdownActorSystem,
          stableAfter
        )
      )
  }
}

private[autodown] object OldestAutoDown {
  def props(oldestMemberRole: Option[String],
            downIfAlone: Boolean,
            shutdownActorSystem: Boolean,
            autoDownUnreachableAfter: FiniteDuration): Props =
    Props(
      new OldestAutoDown(
        oldestMemberRole,
        downIfAlone,
        shutdownActorSystem,
        autoDownUnreachableAfter
      )
    )
}

private[autodown] class OldestAutoDown(oldestMemberRole: Option[String],
                                       downIfAlone: Boolean,
                                       shutdownActorSystem: Boolean,
                                       autoDownUnreachableAfter: FiniteDuration)
    extends OldestAutoDownBase(
      oldestMemberRole,
      downIfAlone,
      autoDownUnreachableAfter
    )
    with ClusterCustomDowning {

  override protected def down(node: Address): Unit = {
    log.info("Down: Oldest is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }

  override protected def shutdownSelf(): Unit = {
    if (shutdownActorSystem) {
      Await.result(context.system.terminate(), 10 seconds)
    } else {
      throw new SplitBrainResolvedError("OldestAutoDowning")
    }
  }

}
