package cn.hezhaohui.thumb.manager.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CacheManager {
    private TopK hotKeyDetector;
    private Cache<String, Object> localCache;

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

    // 构造复合 Key
    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }

    public Object get(String hashKey, String key) {
        String compositeKey = this.buildCacheKey(hashKey, key);
        // 1. 查本地缓存
        Object value = localCache.getIfPresent(compositeKey);
        if (value != null) {
            log.info("本地缓存获取到数据 {} = {}", compositeKey, value);
            // 记录访问次数
            hotKeyDetector.add(key, 1);
            return value;
        }
        // 2. 本地缓存未命中，查询 Redis
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if (redisValue == null) {
            return null;
        }

        // 3. 记录访问次数
        AddResult addResult = hotKeyDetector.add(key, 1);

        // 4. 缓存数据 热Key
        if (addResult.isHotKey()) {
            localCache.put(compositeKey, redisValue);
        }

        return redisValue;
    }

    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = this.buildCacheKey(hashKey, key);
        Object object = localCache.getIfPresent(compositeKey);
        if (object == null) {
            return;
        }
        localCache.put(compositeKey, value);
    }

    // 定时清理过期的 HotKey 数据
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
    }
}
