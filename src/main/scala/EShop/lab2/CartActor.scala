package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)    extends Command
  case class RemoveItem(item: Any) extends Command
  case object ExpireCart           extends Command
  case object StartCheckout        extends Command
  case object CancelCheckout       extends Command
  case object CloseCheckout        extends Command
  case object GetItems             extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef, cart: Cart) extends Event
  case class ItemAdded(itemId: Any, cart: Cart)                 extends Event
  case class ItemRemoved(itemId: Any, cart: Cart)               extends Event
  case object CartEmptied                                       extends Event
  case object CartExpired                                       extends Event
  case object CheckoutClosed                                    extends Event
  case class CheckoutCancelled(cart: Cart)                      extends Event

  def props() = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log = Logging(context.system, this)
  val cartTimerDuration = 5 seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def receive: Receive = empty

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      log.debug("Item " + item + " added to the cart (becoming nonEmpty)")
      context become nonEmpty(Cart.empty.addItem(item), scheduleTimer)

    case GetItems =>
      sender() ! Seq.empty[Any]
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val newCart = cart.removeItem(item)
        if (newCart.size != 0) {
          log.debug("Item " + item + " removed from the cart (becoming empty)")
          context become nonEmpty(newCart, timer)
        }
        else {
          timer.cancel()
          log.debug("Item " + item + " removed from the cart")
          context become empty
        }
      } else {
        log.debug("Trying to remove " + item + ", that is not in the cart")
      }

    case AddItem(item) =>
      log.debug("Item " + item + " added to the cart")
      context become nonEmpty(cart.addItem(item), timer)

    case StartCheckout =>
      timer.cancel()
      log.debug("Starting checkout (becoming inCheckout)")
      val checkoutActor = context.system.actorOf(Checkout.props(self))
      checkoutActor ! Checkout.StartCheckout
      sender() ! CheckoutStarted(checkoutActor)
      context become inCheckout(cart)

    case ExpireCart =>
      log.debug("Time out (becoming empty)")
      context become empty

    case GetItems =>
      sender() ! cart.items
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      log.debug("Canceling checkout (becoming nonEmpty)")
      context become nonEmpty(cart, scheduleTimer)

    case CloseCheckout =>
      log.debug("Closing checkout (becoming empty)")
      context become empty
  }
}
