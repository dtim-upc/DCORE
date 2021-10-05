package dcer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.NotUsed
import akka.Done
import akka.actor.typed.{DispatcherSelector, Terminated}
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.funspec.AnyFunSpecLike

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object IntroSpec {

  object HelloWorld {
    final case class Greet(whom: String, replyTo: ActorRef[Greeted])
    final case class Greeted(whom: String, from: ActorRef[Greet])

    def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
      context.log.info("Hello {}!", message.whom)
      println(s"Hello ${message.whom}!")
      message.replyTo ! Greeted(message.whom, context.self)
      Behaviors.same
    }
  }

  object HelloWorldBot {
    def apply(max: Int): Behavior[HelloWorld.Greeted] = {
      bot(0, max)
    }

    private def bot(
        greetingCounter: Int,
        max: Int
    ): Behavior[HelloWorld.Greeted] =
      Behaviors.receive { (context, message) =>
        val n = greetingCounter + 1
        context.log.info2("Greeting {} for {}", n, message.whom)
        println(s"Greeting $n for ${message.whom}")
        if (n == max) {
          Behaviors.stopped
        } else {
          message.from ! HelloWorld.Greet(message.whom, context.self)
          bot(n, max)
        }
      }
  }

  object HelloWorldMain {
    final case class SayHello(name: String)
    def apply(): Behavior[SayHello] =
      Behaviors.setup { context =>
        val greeter = context.spawn(HelloWorld(), "greeter")

        Behaviors.receiveMessage { message =>
          val replyTo = context.spawn(HelloWorldBot(max = 3), message.name)
          greeter ! HelloWorld.Greet(message.name, replyTo)
          Behaviors.same
        }
      }

    def main(args: Array[String]): Unit = {
      val system: ActorSystem[HelloWorldMain.SayHello] =
        ActorSystem(HelloWorldMain(), "hello")

      system ! HelloWorldMain.SayHello("World")
      system ! HelloWorldMain.SayHello("Akka")
    }
  }

  HelloWorldMain.main(Array.empty)

  object CustomDispatchersExample {
    object HelloWorldMain {

      final case class SayHello(name: String)

      def apply(): Behavior[SayHello] =
        Behaviors.setup { context =>
          val dispatcherPath = "akka.actor.default-blocking-io-dispatcher"

          val props = DispatcherSelector.fromConfig(dispatcherPath)
          val greeter = context.spawn(HelloWorld(), "greeter", props)

          Behaviors.receiveMessage { message =>
            val replyTo = context.spawn(HelloWorldBot(max = 3), message.name)

            greeter ! HelloWorld.Greet(message.name, replyTo)
            Behaviors.same
          }
        }
    }
  }

  object ChatRoom {
    sealed trait RoomCommand
    final case class GetSession(
        screenName: String,
        replyTo: ActorRef[SessionEvent]
    ) extends RoomCommand
    private final case class PublishSessionMessage(
        screenName: String,
        message: String
    ) extends RoomCommand

    sealed trait SessionEvent
    final case class SessionGranted(handle: ActorRef[PostMessage])
        extends SessionEvent
    final case class SessionDenied(reason: String) extends SessionEvent
    final case class MessagePosted(screenName: String, message: String)
        extends SessionEvent

    sealed trait SessionCommand
    final case class PostMessage(message: String) extends SessionCommand
    private final case class NotifyClient(message: MessagePosted)
        extends SessionCommand

    def apply(): Behavior[RoomCommand] =
      chatRoom(List.empty)

    private def chatRoom(
        sessions: List[ActorRef[SessionCommand]]
    ): Behavior[RoomCommand] =
      Behaviors.receive { (context, message) =>
        message match {
          case GetSession(screenName, client) =>
            // create a child actor for further interaction with the client
            val ses = context.spawn(
              session(context.self, screenName, client),
              name = URLEncoder.encode(screenName, StandardCharsets.UTF_8.name)
            )
            client ! SessionGranted(ses)
            chatRoom(ses :: sessions)
          case PublishSessionMessage(screenName, message) =>
            val notification = NotifyClient(MessagePosted(screenName, message))
            sessions.foreach(_ ! notification)
            Behaviors.same
        }
      }

    private def session(
        room: ActorRef[PublishSessionMessage],
        screenName: String,
        client: ActorRef[SessionEvent]
    ): Behavior[SessionCommand] =
      Behaviors.receiveMessage {
        case PostMessage(message) =>
          // from client, publish to others via the room
          room ! PublishSessionMessage(screenName, message)
          Behaviors.same
        case NotifyClient(message) =>
          // published from the room
          client ! message
          Behaviors.same
      }
  }

  object Gabbler {
    import ChatRoom._

    def apply(): Behavior[SessionEvent] =
      Behaviors.setup { context =>
        Behaviors.receiveMessage {
          // We document that the compiler warns about the missing handler for `SessionDenied`
          case SessionDenied(reason) =>
            context.log.info("cannot start chat room session: {}", reason)
            Behaviors.stopped
          case SessionGranted(handle) =>
            handle ! PostMessage("Hello World!")
            Behaviors.same
          case MessagePosted(screenName, message) =>
            context.log.info2(
              "message has been posted by '{}': {}",
              screenName,
              message
            )
            Behaviors.stopped
        }
      }
  }

  object Main {
    def apply(): Behavior[NotUsed] =
      Behaviors.setup { context =>
        val chatRoom = context.spawn(ChatRoom(), "chatroom")
        val gabblerRef = context.spawn(Gabbler(), "gabbler")
        context.watch(gabblerRef)
        chatRoom ! ChatRoom.GetSession("ol’ Gabbler", gabblerRef)

        Behaviors.receiveSignal { case (_, Terminated(_)) =>
          Behaviors.stopped
        }
      }

    def main(args: Array[String]): Unit = {
      val _ = ActorSystem(Main(), "ChatRoomDemo")
    }

  }

}

class IntroSpec
    extends ScalaTestWithActorTestKit
    with AnyFunSpecLike
    with LogCapturing // https://doc.akka.io/docs/akka/current/typed/testing-async.html#silence-logging-output-from-tests
    {

  import IntroSpec._

  describe("Intro sample") {
    it("say hello") {
      val system: ActorSystem[HelloWorldMain.SayHello] =
        ActorSystem(HelloWorldMain(), "hello")

      system ! HelloWorldMain.SayHello("World")
      system ! HelloWorldMain.SayHello("Akka")

      Thread.sleep(500)
      ActorTestKit.shutdown(system)
    }

    it("chat") {
      val system = ActorSystem(Main(), "ChatRoomDemo")
      assert(system.whenTerminated.futureValue === Done)
    }
  }

}
