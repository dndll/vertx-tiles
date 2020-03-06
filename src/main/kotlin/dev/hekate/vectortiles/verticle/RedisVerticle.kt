package dev.hekate.vectortiles.verticle

import dev.hekate.vectortiles.GET_TILE_ADDRESS
import dev.hekate.vectortiles.GET_TILE_REDIS_ADDRESS
import dev.hekate.vectortiles.SETTINGS_MAP
import dev.hekate.vectortiles.sql.getTileQuery
import io.vertx.core.AbstractVerticle
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.Message
import io.vertx.core.net.SocketAddress
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import io.vertx.redis.client.Response
import java.util.*
import java.util.logging.Logger

class RedisVerticle : AbstractVerticle() {
  private val logger = Logger.getLogger(this::class.qualifiedName)

  override fun start() {
    val redis = connectRedis()!!

    fun setTileInCache(cacheKey: String, response: Buffer) {
      redis.set(mutableListOf(cacheKey, Base64.getEncoder().encodeToString(response.bytes))) {
        logger.info("Successfully saved tile with key $cacheKey to the cache")
      }
    }

    fun generateTile(tileQuery: String, cacheKey: String, message: Message<String>) {
      vertx.eventBus().request<Buffer>(GET_TILE_ADDRESS, tileQuery) { asyncResult ->
        val response = asyncResult.result().body()
        message.reply(response)
        setTileInCache(cacheKey, response)
      }
    }

    fun replyWithTile(message: Message<String>, response: Response) {
      message.reply(Buffer.buffer(Base64.getDecoder().decode(response.toBytes())))
    }

    fun getTileFromCache(message: Message<String>) {
      val tileQuery = getTileQuery(message.body().split(","))

      redis.get(message.body()) { ar ->
        if (ar.succeeded()) {
          val response = ar.result()
          if (response == null) {
            logger.fine("Couldn't find tile with key: $${message.body()} in the Cache, generating from the Database")
            generateTile(tileQuery, message.body(), message)
          } else {
            logger.fine("Found Tile with Key: $${message.body()} in the Cache.")
            replyWithTile(message, response)
          }
        } else {
          logger.warning("There was an issue connecting to the Cache, generating from the Database")
          generateTile(tileQuery, message.body(), message)
        }
      }
    }

    vertx.eventBus().consumer<String>(GET_TILE_REDIS_ADDRESS).handler { message ->
      getTileFromCache(message)
    }

    super.start()
  }

  private fun connectRedis(): RedisAPI? {
    val host = SETTINGS_MAP["REDIS_HOST"] ?: "localhost"
    val redisOptions = RedisOptions()
    redisOptions.endpoint = SocketAddress.inetSocketAddress(6379, host)
    val client = Redis.createClient(vertx, redisOptions)
      .connect { onConnect ->
        if (onConnect.succeeded()) {
          logger.info("Redis connection to succeeded with client options: ${redisOptions.toJson()}")
        } else {
          throw onConnect.cause()
        }
      }
    return RedisAPI.api(client)
  }
}
