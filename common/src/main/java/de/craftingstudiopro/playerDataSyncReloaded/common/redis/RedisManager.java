package de.craftingstudiopro.playerDataSyncReloaded.common.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class RedisManager {
    private final String host, password;
    private final int port;
    private final boolean ssl;
    private JedisPool jedisPool;
    private final Logger logger;
    private final String channel = "pdasync_sync_notify";

    public RedisManager(Logger logger, String host, int port, String password, boolean ssl) {
        this.logger = logger;
        this.host = host;
        this.port = port;
        this.password = password;
        this.ssl = ssl;
    }

    public void init() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        
        if (password == null || password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, ssl);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password, ssl);
        }
        
        logger.info("Redis connection established.");
    }

    public void subscribe(Consumer<String> messageConsumer) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        messageConsumer.accept(message);
                    }
                }, channel);
            } catch (Exception e) {
                logger.severe("Redis subscription failed: " + e.getMessage());
            }
        });
    }

    public void publish(String message) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                logger.severe("Redis publish failed: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (jedisPool != null) jedisPool.close();
    }
}
