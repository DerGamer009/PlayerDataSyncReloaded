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
    private volatile boolean closed;
    private volatile JedisPubSub pubSub;

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

    /**
     * Subscribes on a dedicated thread. If the connection drops, the thread waits 5 seconds and
     * subscribes again until {@link #close()} is called.
     */
    public void subscribe(Consumer<String> messageConsumer) {
        Thread thread = new Thread(() -> {
            while (!closed) {
                try (Jedis jedis = jedisPool.getResource()) {
                    pubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            try {
                                messageConsumer.accept(message);
                            } catch (Exception e) {
                                logger.warning("Redis message handler failed: " + e.getMessage());
                            }
                        }
                    };
                    jedis.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (closed) {
                        break;
                    }
                    logger.warning("Redis subscription lost (" + e.getMessage() + "), reconnecting in 5s.");
                }

                if (!closed) {
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "PDS-Redis-Subscriber");
        thread.setDaemon(true);
        thread.start();
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
        closed = true;
        JedisPubSub active = pubSub;
        if (active != null) {
            try {
                active.unsubscribe();
            } catch (Exception ignored) {
                // connection already gone
            }
        }
        if (jedisPool != null) jedisPool.close();
    }
}
