package mi.yxz.mizu.services

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.Serializable



class RedisService(
    private val redisURI: RedisURI
) {
    private val client: RedisClient = RedisClient.create(redisURI)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisCommands<String, String> = connection.sync()
}