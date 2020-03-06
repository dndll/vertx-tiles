package dev.hekate.vectortiles.verticle

import dev.hekate.vectortiles.GET_TILE_ADDRESS
import dev.hekate.vectortiles.SETTINGS_MAP
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.pgclient.PgConnectOptions
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.PoolOptions
import java.util.logging.Logger

class PostgresVerticle: AbstractVerticle() {
  private val logger = Logger.getLogger(this::class.qualifiedName)

  override fun start() {
    val clientPool: PgPool = initializePostgresClientPool()

    fun getTile(message: Message<String>) {
      val tileQuery = message.body()

      clientPool.getConnection { initialConnection ->
        if (initialConnection.succeeded()) {
          val connection = initialConnection.result()
          connection.query(tileQuery) { asyncResult ->
            if (asyncResult.succeeded()) {
              val rowSet = asyncResult.result()
              val iterator = rowSet.iterator()
              val buffer = Buffer.buffer()
              while (iterator.hasNext()) {
                buffer.appendBuffer(iterator.next().getBuffer(0))
              }
              message.reply(buffer)
            } else {
              val logMessage = "TileQuery failed for query: $tileQuery. Reason: ${initialConnection.cause()}"
              logger.severe(logMessage)
              message.fail(500, logMessage)
            }
            connection.close()
          }
        } else {
          val logMessage = "InitialConnection failed, reason: ${initialConnection.cause()}"
          logger.severe(logMessage)
          message.fail(500, logMessage)
        }
      }
    }

    vertx.eventBus().consumer<String>(GET_TILE_ADDRESS).handler { message ->
      getTile(message)
    }

    super.start()
  }

  private fun initializePostgresClientPool(): PgPool {
    val host = SETTINGS_MAP["POSTGIS_HOST"] ?: "localhost"
    val user = SETTINGS_MAP["POSTGIS_USER"] ?: "postgres"
    val poolSize = SETTINGS_MAP["POSTGRES_CLIENT_POOL_SIZE"]?.toInt() ?: 5
    val connectOptions: PgConnectOptions =
      PgConnectOptions()
        .setPort(5432)
        .setHost(host)
        .setDatabase("gis")
        .setUser("gis")
        .setPassword("gis")

    val poolOptions: PoolOptions = PoolOptions()
      .setMaxSize(poolSize)

    logger.info("Initialised Postgres Client with options: ${connectOptions.toJson()} and pool options: ${poolOptions.toJson()}")
    return PgPool.pool(vertx, connectOptions, poolOptions)
  }
}
