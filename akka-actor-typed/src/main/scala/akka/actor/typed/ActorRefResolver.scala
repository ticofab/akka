/*
 * Copyright (C) 2017-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.actor.typed

import akka.actor.ActorRefWithCell
import akka.actor.ExtendedActorSystem
import akka.annotation.DoNotInherit
import akka.annotation.InternalApi

object ActorRefResolver extends ExtensionId[ActorRefResolver] {
  def get(system: ActorSystem[_]): ActorRefResolver = apply(system)

  override def createExtension(system: ActorSystem[_]): ActorRefResolver =
    new ActorRefResolverImpl(system)
}

/**
 * Serialization and deserialization of `ActorRef`.
 *
 * This class is not intended for user extension other than for test purposes (e.g.
 * stub implementation). More methods may be added in the future and that may break
 * such implementations.
 */
@DoNotInherit
abstract class ActorRefResolver extends Extension {

  /**
   * Generate full String representation including the uid for the actor cell
   * instance as URI fragment, replacing the Address in the RootActor Path
   * with the local one unless this path’s address includes host and port
   * information. This representation should be used as serialized
   * representation.
   */
  def toSerializationFormat[T](ref: ActorRef[T]): String

  /**
   * Deserialize an `ActorRef` in the [[#toSerializationFormat]].
   */
  def resolveActorRef[T](serializedActorRef: String): ActorRef[T]

}

/**
 * INTERNAL API
 */
@InternalApi private[akka] class ActorRefResolverImpl(system: ActorSystem[_]) extends ActorRefResolver {
  import akka.actor.typed.scaladsl.adapter._

  private val untypedSystem = system.toUntyped.asInstanceOf[ExtendedActorSystem]

  override def toSerializationFormat[T](ref: ActorRef[T]): String = {

    def toSerializationFormatWithAddress =
      ref.path.toSerializationFormatWithAddress(untypedSystem.provider.getDefaultAddress)

    ref.toUntyped match {
      case a: ActorRefWithCell =>
        val originSystem = a.underlying.system.asInstanceOf[ExtendedActorSystem]
        if (originSystem eq untypedSystem)
          toSerializationFormatWithAddress
        else
          throw new IllegalArgumentException(
            s"ActorRefResolver for ActorSystem [${untypedSystem.provider.getDefaultAddress}] shouldn't be for " +
            "serialization of ActorRef that originates from another ActorSystem " +
            s"[${originSystem.provider.getDefaultAddress}]. Use the ActorRefResolver for that system instead.")

      case _ =>
        // no origin system information for RemoteActorRef or MinimalActorRef, so just use the
        // one for this extension. That is correct for RemoteActorRef, but MinimalActorRef
        // could be wrong. However, since we don't allow usage of "wrong" ActorSystem for
        // ordinary ActorRef the users will learn not do that mistake.
        toSerializationFormatWithAddress
    }
  }

  override def resolveActorRef[T](serializedActorRef: String): ActorRef[T] =
    untypedSystem.provider.resolveActorRef(serializedActorRef)
}

object ActorRefResolverSetup {
  def apply[T <: Extension](createExtension: ActorSystem[_] => ActorRefResolver): ActorRefResolverSetup =
    new ActorRefResolverSetup(new java.util.function.Function[ActorSystem[_], ActorRefResolver] {
      override def apply(sys: ActorSystem[_]): ActorRefResolver = createExtension(sys)
    }) // TODO can be simplified when compiled only with Scala >= 2.12

}

/**
 * Can be used in [[akka.actor.setup.ActorSystemSetup]] when starting the [[ActorSystem]]
 * to replace the default implementation of the [[ActorRefResolver]] extension. Intended
 * for tests that need to replace extension with stub/mock implementations.
 */
final class ActorRefResolverSetup(createExtension: java.util.function.Function[ActorSystem[_], ActorRefResolver])
    extends ExtensionSetup[ActorRefResolver](ActorRefResolver, createExtension)
