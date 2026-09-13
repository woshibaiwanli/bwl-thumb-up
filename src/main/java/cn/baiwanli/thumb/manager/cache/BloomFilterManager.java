package cn.baiwanli.thumb.manager.cache;

import cn.baiwanli.thumb.mapper.ThumbMapper;
import cn.baiwanli.thumb.model.entity.Thumb;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 布隆过滤器管理器
 * 用于防止缓存穿透：拦截不存在的 Key，避免请求穿透到 Redis/DB
 *
 * 设计要点：
 * 1. 预期插入量 100万，误判率 1%（约 9.6MB 内存）
 * 2. 系统启动时初始化，运行期间持续添加新 Key
 * 3. 布隆过滤器存在假阳性（误判为存在），但不存在假阴性（不会漏判不存在的 Key）
 */
@Component
@Slf4j
public class BloomFilterManager {

    /**
     * 布隆过滤器：存储 userId:blogId 组合
     * Key 格式："{userId}:{blogId}"
     */
    private BloomFilter<String> thumbBloomFilter;

    /**
     * 预期插入量
     */
    private static final long EXPECTED_INSERTIONS = 1_000_000L;

    /**
     * 误判率
     */
    private static double FPP = 0.01;

    @Resource
    private ThumbMapper thumbMapper;

    @PostConstruct
    public void init() {
        thumbBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(java.nio.charset.StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FPP
        );
        // 从数据库加载所有历史点赞记录到布隆过滤器
        LambdaQueryWrapper<Thumb> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Thumb::getUserid, Thumb::getBlogId);
        thumbMapper.selectList(queryWrapper).forEach(thumb ->
                thumbBloomFilter.put(buildKey(thumb.getUserid(), thumb.getBlogId()))
        );
        log.info("布隆过滤器初始化完成，预期插入量: {}, 误判率: {}", EXPECTED_INSERTIONS, FPP);
    }

    /**
     * 构建布隆过滤器 Key
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return 复合 Key
     */
    private String buildKey(Long userId, Long blogId) {
        return userId + ":" + blogId;
    }

    /**
     * 添加点赞记录到布隆过滤器
     * 在用户成功点赞后调用
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     */
    public void add(Long userId, Long blogId) {
        thumbBloomFilter.put(buildKey(userId, blogId));
    }

    /**
     * 判断用户是否可能点赞过该博客
     *
     * @param userId 用户 ID
     * @param blogId 博客 ID
     * @return false = 一定没有点赞过（可以安全拦截）
     *         true = 可能点赞过（需要进一步查询 Redis/DB）
     */
    public boolean mightContain(Long userId, Long blogId) {
        return thumbBloomFilter.mightContain(buildKey(userId, blogId));
    }

    /**
     * 判断用户是否可能点赞过该博客（String 类型参数）
     *
     * @param userId 用户 ID（字符串）
     * @param blogId 博客 ID（字符串）
     * @return false = 一定没有点赞过
     *         true = 可能点赞过
     */
    public boolean mightContain(String userId, String blogId) {
        return thumbBloomFilter.mightContain(userId + ":" + blogId);
    }
}
