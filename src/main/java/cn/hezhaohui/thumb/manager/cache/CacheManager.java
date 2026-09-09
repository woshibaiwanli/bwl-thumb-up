package cn.hezhaohui.thumb.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {
    private TopK hotKeyDetector;
    private Cache<String, Object> localCache;

    /**
     * 互斥锁 Map：用于缓存击穿防护
     * Key 为 compositeKey，Value 为锁对象
     * 使用 ConcurrentHashMap 管理锁对象，避免 String.intern() 内存泄漏
     */
    private final ConcurrentHashMap<String, Object> lockMap = new ConcurrentHashMap<>();

    /**
     * 空值标记：用于缓存穿透防护
     * 当 Redis 查询返回 null 时，写入此标记到本地缓存（短 TTL）
     */
    private static final Object NULL_PLACEHOLDER = new Object();

    /**
     * 空值短缓存过期时间（秒）
     */
    private static final long NULL_CACHE_TTL_SECONDS = 30;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Bean
    public TopK getHotKeyDetector() {
        return hotKeyDetector = new HeavyKeeper(
                // 监控 Top 100 Key
                100,
                // 宽度
                100000,
                // 深度
                5,
                // 衰减系数
                0.92,
                // 最小出现 10 次才记录
                10
        );
    }

    @Bean
    public Cache<String, Object> localCache() {
        return localCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 创建空值短缓存（独立的 Caffeine 实例，TTL 更短）
     * 用于缓存穿透防护：缓存不存在的 Key，避免反复查询 Redis
     */
    private final Cache<String, Object> nullValueCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(NULL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    // 构造复合 Key
    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    /**
     * 查询缓存（带缓存穿透防护 + 缓存击穿防护）
     *
     * 查询流程：
     * 1. 先查本地缓存（L1）→ 命中则返回
     * 2. 再查空值短缓存 → 命中则返回 null（避免穿透）
     * 3. 加互斥锁，double-check 本地缓存
     * 4. 查 Redis（L2）→ 未命中则写入空值短缓存
     * 5. Redis 命中 → 记录访问次数 → 热 Key 提升到 L1
     *
     * @param hashKey Redis Hash Key
     * @param key     Redis Hash Field
     * @return 缓存值，null 表示不存在
     */
    public Object get(String hashKey, String key) {
        String compositeKey = this.buildCacheKey(hashKey, key);

        // 1. 查本地缓存（L1）
        Object value = localCache.getIfPresent(compositeKey);
        if (value != null) {
            log.info("本地缓存获取到数据 {} = {}", compositeKey, value);
            // 记录访问次数
            hotKeyDetector.add(key, 1);
            return value;
        }

        // 2. 查空值短缓存（防缓存穿透）
        Object nullMarker = nullValueCache.getIfPresent(compositeKey);
        if (nullMarker != null) {
            log.debug("空值短缓存命中，Key: {}", compositeKey);
            return null;
        }

        // 3. 加互斥锁，防止缓存击穿（热 Key 过期时单线程回源）
        Object lock = lockMap.computeIfAbsent(compositeKey, k -> new Object());
        synchronized (lock) {
            try {
                // double-check：再次检查本地缓存（可能其他线程已回源完成）
                value = localCache.getIfPresent(compositeKey);
                if (value != null) {
                    log.info("double-check 本地缓存命中 {} = {}", compositeKey, value);
                    hotKeyDetector.add(key, 1);
                    return value;
                }

                // 4. 查询 Redis（L2）
                Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
                if (redisValue == null) {
                    // 防缓存穿透：写入空值短缓存
                    nullValueCache.put(compositeKey, NULL_PLACEHOLDER);
                    log.debug("Redis 未命中，写入空值短缓存，Key: {}", compositeKey);
                    return null;
                }

                // 5. 记录访问次数
                AddResult addResult = hotKeyDetector.add(key, 1);

                // 6. 热 Key 提升到本地缓存
                if (addResult.isHotKey()) {
                    localCache.put(compositeKey, redisValue);
                    log.info("热 Key 提升到本地缓存: {}", compositeKey);
                }

                return redisValue;
            } finally {
                // 清理锁对象（可选：避免 lockMap 无限膨胀）
                // 注意：这里不清理，因为 computeIfAbsent 会复用已有锁对象
                // 若需清理，可在锁内判断是否还有其他线程等待
            }
        }
    }

    /**
     * 更新本地缓存（仅更新已存在的 Key）
     * 用于写操作后保持 L1 缓存一致性
     *
     * @param hashKey Redis Hash Key
     * @param key     Redis Hash Field
     * @param value   新值
     */
    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = this.buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null) {
            return;
        }
        localCache.put(compositeKey, value);
    }

    /**
     * 失效本地缓存
     * 用于写操作后主动清除缓存
     *
     * @param hashKey Redis Hash Key
     * @param key     Redis Hash Field
     */
    public void evict(String hashKey, String key) {
        String compositeKey = this.buildCacheKey(hashKey, key);
        localCache.invalidate(compositeKey);
        nullValueCache.invalidate(compositeKey);
    }

    // 定时清理过期的 HotKey 数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }
}
