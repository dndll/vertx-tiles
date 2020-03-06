package dev.hekate.vectortiles

import dev.hekate.vectortiles.verticle.PostgresVerticle
import dev.hekate.vectortiles.verticle.RedisVerticle
import io.vertx.config.ConfigRetriever
import io.vertx.core.AbstractVerticle
import io.vertx.core.Launcher
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import java.util.logging.Logger

class MainVerticle : AbstractVerticle() {
  private val logger = Logger.getLogger(this::class.qualifiedName)

  @Throws(Exception::class)
  override fun start(startPromise: Promise<Void>) {
    // Setup
    applyConfiguration()
    deployVerticles()
    val router = setupRouter()!!

    // Routes
    router.get("/tiles/").handler(::serviceHandler)
    router.get("/tiles/service").handler(::serviceHandler)
    router.get("/tiles/health").handler(::healthHandler)
    router.get("/tiles/:layername/:z/:x/:y").handler(::getTileHandler)

    // Initialise server
    vertx.createHttpServer().requestHandler(router).listen(SETTINGS_MAP["PORT"]?.toInt() ?: 8752) { http ->
      if (http.succeeded()) {
        startPromise.complete()
        logger.info("HTTP server started on port ${SETTINGS_MAP["PORT"]}")
      } else {
        startPromise.fail(http.cause())
      }
    }
  }

  private fun setupRouter(): Router? {
    val router = Router.router(vertx)
    enableCors(router)
    return router
  }

  private fun deployVerticles() {
    vertx.deployVerticle(PostgresVerticle())
    vertx.deployVerticle(RedisVerticle())
  }

  private fun applyConfiguration() {
    var config: JsonObject? = null
    val configRetriever = ConfigRetriever.create(vertx)
    configRetriever.getConfig { json ->
      if (json.failed()) {
        logger.severe("Config failed to initialise")
      } else {
        logger.info("Config initialised")
        config = json.result()
      }
    }
    SETTINGS_MAP["POSTGIS_HOST"] = config?.getString("POSTGIS_HOST", "127.0.0.1") ?: "127.0.0.1"
    SETTINGS_MAP["POSTGIS_USER"] = config?.getString("POSTGIS_USER", "127.0.0.1") ?: "127.0.0.1"
    SETTINGS_MAP["REDIS_HOST"] = config?.getString("REDIS_HOST", "127.0.0.1") ?: "127.0.0.1"
    SETTINGS_MAP["PORT"] = config?.getInteger("PORT", 8752).toString()
  }

  private fun getTileHandler(routingContext: RoutingContext) {
    val layerName = routingContext.request().getParam("layername")
    val z = routingContext.request().getParam("z")
    val x = routingContext.request().getParam("x")
    val y = routingContext.request().getParam("y")

    vertx.eventBus().request<Buffer>(GET_TILE_REDIS_ADDRESS, "$z,$x,$y,$layerName") { asyncResult ->
      if (asyncResult.succeeded()) {
        val buffer = asyncResult.result().body()
        routingContext.response().putHeader("content-type", "application/x-protobuf")
        routingContext.response().putHeader("Content-Length", buffer.bytes.size.toString())
        routingContext.response().write(buffer)
        routingContext.response().end()
      }
    }
  }

  private fun healthHandler(it: RoutingContext) {
    it.response().putHeader("Content-Type", "application/json")
    it.response().end(JsonObject().put("status", "UP").toBuffer())
  }

  private fun serviceHandler(it: RoutingContext) {
    val json = JsonObject()
      .put("title", "Vector-Tiles")
      .put("description", "A tile server dynamically generating Mapbox Vector tiles based from OSM. " +
        "This service has most osm feature layers available in the dataset, just substitute the layer name and have fun!")
      .put("references", listOf("https://docs.mapbox.com/vector-tiles/reference/"))
      .put("tags", listOf("MVT", "PBF", "Vector", "Tiles"))
    it.response().putHeader("Content-Type", "application/json")
    it.response().end(json.encode())
  }

  private fun enableCors(router: Router) {
    val allowedHeaders = hashSetOf<String>()
    allowedHeaders.add("x-requested-with")
    allowedHeaders.add("Access-Control-Allow-Origin")
    allowedHeaders.add("origin")
    allowedHeaders.add("Content-Type")
    allowedHeaders.add("accept")
    allowedHeaders.add("X-PINGARUNER")

    val allowedMethods = hashSetOf<HttpMethod>()
    allowedMethods.add(HttpMethod.GET)
    allowedMethods.add(HttpMethod.POST)
    allowedMethods.add(HttpMethod.OPTIONS)
    allowedMethods.add(HttpMethod.DELETE)
    allowedMethods.add(HttpMethod.PATCH)
    allowedMethods.add(HttpMethod.PUT)

    router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods))
  }

  @Throws(Exception::class)
  override fun stop(stopPromise: Promise<Void>) {
    super.stop(stopPromise)
  }

  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      Launcher.executeCommand("run", MainVerticle::class.java.name)
    }
  }
}
