package cn.hezhaohui.thumb.service.impl;

import cn.hezhaohui.thumb.constant.ThumbConstant;
import cn.hezhaohui.thumb.exception.BusinessException;
import cn.hezhaohui.thumb.exception.ErrorCode;
import cn.hezhaohui.thumb.manager.cache.BloomFilterManager;
import cn.hezhaohui.thumb.manager.cache.CacheManager;
import cn.hezhaohui.thumb.model.dto.DoThumbRequest;
import cn.hezhaohui.thumb.model.entity.Blog;
import cn.hezhaohui.thumb.model.entity.User;
import cn.hezhaohui.thumb.service.BlogService;
import cn.hezhaohui.thumb.service.UserService;
import cn.hezhaohui.thumb.util.RedisKeyUtil;
import cn.hezhaohui.thumb.util.RedisLockUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hezhaohui.thumb.model.entity.Thumb;
import cn.hezhaohui.thumb.service.ThumbService;
import cn.hezhaohui.thumb.mapper.ThumbMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author 23117
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-10-18 14:51:00
 *
 * 改进点：
 * 1. 使用 Redis 分布式锁替代 synchronized + intern()，支持多实例部署，避免内存泄漏
 * 2. 集成布隆过滤器，防止缓存穿透
 * 3. 使用 CacheManager 的二级缓存（含空值短缓存 + 互斥锁防击穿）
 */
@Service("thumbService")
@Slf4j
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
        implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CacheManager cacheManager;

    @Resource
    private RedisLockUtil redisLockUtil;

    @Resource
    private BloomFilterManager bloomFilterManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();

        // 分布式锁：对用户加锁，确保同一用户的并发请求串行执行
        // 替代原来的 synchronized + intern()，解决内存泄漏问题，支持多实例部署
        String lockKey = "USERID:" + userId;
        return redisLockUtil.executeWithLock(lockKey, () -> {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                // 判断是否已点过赞
                Boolean exists = this.hasThumb(blogId, userId);
                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
                }
                // 更新帖子点赞计数器
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();
                // 更新点赞表数据
                Thumb thumb = new Thumb();
                thumb.setUserid(userId);
                thumb.setBlogId(blogId);
                // 两者一起执行
                boolean success = update && this.save(thumb);
                // 点赞记录存入 Redis + 更新布隆过滤器
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
                    String filedKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, filedKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, filedKey, realThumbId);
                    // 添加到布隆过滤器（防缓存穿透）
                    bloomFilterManager.add(userId, blogId);
                }
                return success;
            });
        });
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();

        // 分布式锁：对用户加锁
        String lockKey = "USERID:" + userId;
        return redisLockUtil.executeWithLock(lockKey, () -> {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                // 判断是否已点过赞
                Object thumbIdObj = cacheManager.get(
                        ThumbConstant.USER_THUMB_KEY_PREFIX + userId,
                        blogId.toString()
                );
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
                }
                Long thumbId = ((Number) thumbIdObj).longValue();
                // 更新帖子点赞计数器
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();
                // 更新点赞表数据
                boolean success = update && this.removeById(thumbId);
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return success;
            });
        });
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        // 布隆过滤器前置判断：若返回 false，则一定没有点赞过，直接返回
        // 这是缓存穿透防护的第一道防线
        if (!bloomFilterManager.mightContain(userId, blogId)) {
            log.debug("布隆过滤器拦截：用户 {} 未点赞博客 {}", userId, blogId);
            return false;
        }

        // 通过布隆过滤器后，查询二级缓存（含空值短缓存 + 互斥锁防击穿）
        Object thumbIdObj = cacheManager.get(
                ThumbConstant.USER_THUMB_KEY_PREFIX + userId,
                blogId.toString()
        );
        if (thumbIdObj == null) {
            return false;
        }
        Long thumbId = ((Number) thumbIdObj).longValue();
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }
}
