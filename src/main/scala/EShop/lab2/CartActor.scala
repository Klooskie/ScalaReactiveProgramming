package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props, Timers}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any) extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart extends Command
  case object StartCheckout extends Command
  case object CancelCheckout extends Command
  case object CloseCheckout extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val newCart = cart.removeItem(item)
        if (newCart.size != 0)
          context become nonEmpty(newCart, timer)
        else {
          timer.cancel()
          context become empty
        }
      }

    case AddItem(item) =>
      context become nonEmpty(cart.addItem(item), timer)

    case StartCheckout =>
      timer.cancel()
      context become inCheckout(cart)

    case ExpireCart =>
      context become empty
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      context become nonEmpty(cart, scheduleTimer)

    case CloseCheckout =>
      context become empty
  }
}
