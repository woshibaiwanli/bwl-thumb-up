package cn.baiwanli.thumb.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具类
 * 基于 Redis SETNX + PX（毫秒过期）实现
 *
 * 设计要点：
 * 1. 使用 SETNX 保证原子性加锁
 * 2. 设置过期时间防止死锁
 * 3. 使用 Lua 脚本保证释放锁的原子性（只释放自己持有的锁）
 * 4. 使用 ThreadLocal 存储锁标识，避免误释放其他线程的锁
 *
 * 适用场景：多实例部署时，需要跨 JVM 的互斥机制
 */
@Component
@Slf4j
public class RedisLockUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 锁前缀
     */
    private static final String LOCK_PREFIX = "lock:thumb:";

    /**
     * 默认锁过期时间（毫秒）
     */
    private static final long DEFAULT_LOCK_TIMEOUT = 10000L;

    /**
     * 默认获取锁超时时间（毫秒）
     */
    private static final long DEFAULT_ACQUIRE_TIMEOUT = 3000L;

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL = 50L;

    /**
     * ThreadLocal 存储当前线程持有的锁标识
     * 用于释放锁时验证是否是自己持有的锁
     */
    private static final ThreadLocal<String> LOCK_VALUE_HOLDER = new ThreadLocal<>();

    /**
     * 释放锁的 Lua 脚本
     * 原子性操作：验证锁标识 → 删除锁
     * 只有锁的持有者才能释放锁，避免误释放
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    /**
     * 构建锁 Key
     *
     * @param lockKey 业务锁 Key
     * @return 完整的 Redis Key
     */
    private String buildLockKey(String lockKey) {
        return LOCK_PREFIX + lockKey;
    }

    /**
     * 生成锁标识（UUID + 线程ID）
     * 确保每个线程的锁标识唯一
     *
     * @return 锁标识
     */
    private String generateLockValue() {
        return UUID.randomUUID().toString() + ":" + Thread.currentThread().getId();
    }

    /**
     * 尝试获取分布式锁（使用默认超时时间）
     *
     * @param lockKey 业务锁 Key（如 "userId:123"）
     * @return true = 获取成功，false = 获取失败
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_ACQUIRE_TIMEOUT, DEFAULT_LOCK_TIMEOUT);
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey       业务锁 Key
     * @param acquireTimeout 获取锁超时时间（毫秒）
     * @param lockTimeout    锁过期时间（毫秒）
     * @return true = 获取成功，false = 获取失败
     */
    public boolean tryLock(String lockKey, long acquireTimeout, long lockTimeout) {
        String fullLockKey = buildLockKey(lockKey);
        String lockValue = generateLockValue();
        long startTime = System.currentTimeMillis();

        while (true) {
            // 尝试 SETNX
            Boolean result = stringRedisTemplate.opsForValue()
                    .setIfAbsent(fullLockKey, lockValue, lockTimeout, TimeUnit.MILLISECONDS);

            if (Boolean.TRUE.equals(result)) {
                // 获取成功，存储锁标识到 ThreadLocal
                LOCK_VALUE_HOLDER.set(lockValue);
                log.debug("获取分布式锁成功，Key: {}, Thread: {}", lockKey, Thread.currentThread().getId());
                return true;
            }

            // 检查是否超时
            if (System.currentTimeMillis() - startTime >= acquireTimeout) {
                log.warn("获取分布式锁超时，Key: {}, Thread: {}", lockKey, Thread.currentThread().getId());
                return false;
            }

            // 短暂等待后重试
            try {
                Thread.sleep(RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("获取分布式锁被中断，Key: {}", lockKey);
                return false;
            }
        }
    }

    /**
     * 释放分布式锁
     * 使用 Lua 脚本保证原子性：只有锁的持有者才能释放锁
     *
     * @param lockKey 业务锁 Key
     * @return true = 释放成功，false = 释放失败（锁已过期或不属于当前线程）
     */
    public boolean unlock(String lockKey) {
        String fullLockKey = buildLockKey(lockKey);
        String lockValue = LOCK_VALUE_HOLDER.get();

        if (lockValue == null) {
            log.warn("释放分布式锁失败：当前线程未持有锁，Key: {}, Thread: {}", lockKey, Thread.currentThread().getId());
            return false;
        }

        try {
            // 执行 Lua 脚本：验证锁标识 → 删除锁
            Long result = stringRedisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(fullLockKey),
                    lockValue
            );

            boolean success = Long.valueOf(1L).equals(result);
            if (success) {
                log.debug("释放分布式锁成功，Key: {}, Thread: {}", lockKey, Thread.currentThread().getId());
            } else {
                log.warn("释放分布式锁失败：锁已过期或不属于当前线程，Key: {}, Thread: {}", lockKey, Thread.currentThread().getId());
            }
            return success;
        } finally {
            // 清理 ThreadLocal
            LOCK_VALUE_HOLDER.remove();
        }
    }

    /**
     * 在锁保护下执行业务逻辑
     * 自动处理加锁和释放锁，确保锁一定会被释放
     *
     * @param lockKey  业务锁 Key
     * @param action   业务逻辑
     * @param <T>      返回值类型
     * @return 业务逻辑的返回值
     * @throws RuntimeException 获取锁失败或业务逻辑异常
     */
    public <T> T executeWithLock(String lockKey, LockAction<T> action) {
        boolean locked = false;
        try {
            locked = tryLock(lockKey);
            if (!locked) {
                throw new RuntimeException("获取分布式锁失败，Key: " + lockKey);
            }
            return action.execute();
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    /**
     * 锁保护下的业务逻辑接口
     *
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface LockAction<T> {
        T execute();
    }
}
