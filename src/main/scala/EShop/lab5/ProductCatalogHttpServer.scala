package EShop.lab5

import EShop.lab5.ProductCatalog.{GetItems, Items}
import akka.actor.ActorSystem
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.RootJsonFormat

import scala.concurrent.duration._

class ProductCatalogHttpServer(actorSystem: ActorSystem) extends HttpApp with JsonSupport {
  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(ProductCatalog.Item)
  implicit val itemsFormat: RootJsonFormat[Items] = jsonFormat1(ProductCatalog.Items)
  implicit val getItemsFormat: RootJsonFormat[GetItems] = jsonFormat2(ProductCatalog.GetItems)

  // ask timeout
  implicit val timeout: Timeout = 5.seconds

  override protected def routes: Route = {
    path("items") {
      post {
        entity(as[GetItems]) { query =>
          complete {
            val productCatalogActor =
              actorSystem.actorSelection("akka.tcp://ProductCatalog@127.0.0.1:2553/user/productcatalog")
            (productCatalogActor ? query).mapTo[Items]
          }
        }
      }
    }
  }
}

object ProductCatalogHttpServer extends App {
  private val config = ConfigFactory.load()
  private val system = ActorSystem(
    "ProductCatalogServerAsys",
    config
      .getConfig("serverAsys")
      .withFallback(config)
  )
  new ProductCatalogHttpServer(system).startServer("localhost", 8888)
}

// pokazowka
// curl -d '{"brand":"gerber", "productKeyWords":["knife", "cream"]}' -H "Content-Type: application/json" -X POST http://127.0.0.1:8888/items
