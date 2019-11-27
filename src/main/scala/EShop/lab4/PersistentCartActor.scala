package EShop.lab4

import EShop.lab2.{Cart, Checkout}
import akka.actor.{Cancellable, Props}
import akka.event.{Logging, LoggingReceive}
import akka.persistence.PersistentActor
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.duration._

object PersistentCartActor {

  def props(persistenceId: String) = Props(new PersistentCartActor(persistenceId))
}

class PersistentCartActor(
  val persistenceId: String
) extends PersistentActor {

  import EShop.lab2.CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 5.seconds

  private def scheduleTimer: Cancellable = context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  override def receiveCommand: Receive = empty

  private def updateState(event: Event, timer: Option[Cancellable] = None): Unit = {
    event match {
      case CartExpired | CheckoutClosed =>
        log.debug("Time out or Checkout closed (becoming empty)")
        context become empty

      case CheckoutCancelled(cart) =>
        log.debug("Canceling checkout (becoming nonEmpty)")
        context become nonEmpty(cart, scheduleTimer)

      case ItemAdded(item, cart) =>
        if (timer.isDefined)
          timer.get.cancel()
        context become nonEmpty(cart.addItem(item), scheduleTimer) //TODO mozliwe ze timer.getOrElse(scheduleTimer)

      case CartEmptied =>
        if (timer.isDefined)
          timer.get.cancel()
        log.debug("Item removed from the cart")
        context become empty

      case ItemRemoved(item, cart) =>
        log.debug("Item " + item + " removed from the cart (becoming non empty)")
        if (timer.isDefined)
          timer.get.cancel()
        context become nonEmpty(cart.removeItem(item), scheduleTimer) //TODO mozliwe ze timer.getOrElse(scheduleTimer)

      case CheckoutStarted(checkoutRef, cart) =>
        if (timer.isDefined)
          timer.get.cancel()
        checkoutRef ! Checkout.StartCheckout
        context become inCheckout(cart)
    }
  }

  def empty: Receive = LoggingReceive {
    case AddItem(item) =>
      persist(ItemAdded(item, Cart.empty)) { event =>
        updateState(event)
      }

    case GetItems =>
      sender() ! Seq.empty[Any]
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = LoggingReceive {
    case RemoveItem(item) =>
      if (cart.contains(item)) {
        val newCart = cart.removeItem(item)
        if (newCart.size != 0) {
          persist(ItemRemoved(item, cart)) { event =>
            updateState(event, Some(timer))
          }
        } else {
          persist(CartEmptied) { event =>
            updateState(event, Some(timer))
          }
        }
      } else {
        log.debug("Trying to remove " + item + ", that is not in the cart")
      }

    case AddItem(item) =>
      persist(ItemAdded(item, cart)) { event =>
        updateState(event, Some(timer))
      }

    case StartCheckout =>
      log.debug("Starting checkout (becoming inCheckout)")
      val checkoutActor = context.system.actorOf(Checkout.props(self))
      sender() ! CheckoutStarted(checkoutActor, cart)
      persist(CheckoutStarted(checkoutActor, cart)) { event =>
        {
          updateState(event, Some(timer))
        }
      }

    case ExpireCart =>
      persist(CartExpired) { event =>
        updateState(event, Some(timer))
      }

    case GetItems =>
      sender() ! cart.items
  }

  def inCheckout(cart: Cart): Receive = LoggingReceive {
    case CancelCheckout =>
      persist(CheckoutCancelled(cart)) { event =>
        updateState(event)
      }

    case CloseCheckout =>
      persist(CheckoutClosed) { event =>
        updateState(event)
      }
  }

  override def receiveRecover: Receive = LoggingReceive {
    case evt: Event => updateState(evt)
  }
}