package EShop.lab2

import EShop.lab2.CartActor.{AddItem, CancelCheckout, CloseCheckout, RemoveItem, StartCheckout}
import akka.actor.{ActorSystem, Props}

object TestCartActorsApp extends App {

  val system = ActorSystem("Reactive2")

  val cartActor = system.actorOf(Props[CartActor], "cartActor")

  cartActor ! AddItem("chleb")
  cartActor ! AddItem("jajka")
  cartActor ! RemoveItem("chleb")
  cartActor ! RemoveItem("pomidory")
  cartActor ! AddItem("ser")
  cartActor ! StartCheckout
  cartActor ! CancelCheckout
  cartActor ! StartCheckout
  cartActor ! CloseCheckout

  val cartFSMActor = system.actorOf(Props[CartFSM], "cartFSMActor")

  cartFSMActor ! AddItem("chleb")
  cartFSMActor ! AddItem("jajka")
  cartFSMActor ! RemoveItem("chleb")
  cartFSMActor ! RemoveItem("pomidory")
  cartFSMActor ! AddItem("ser")
  cartFSMActor ! StartCheckout
  cartFSMActor ! CancelCheckout
  cartFSMActor ! StartCheckout
  cartFSMActor ! CloseCheckout
}
