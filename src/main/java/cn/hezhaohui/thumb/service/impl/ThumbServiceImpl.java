package cn.hezhaohui.thumb.service.impl;

import cn.hezhaohui.thumb.constant.ThumbConstant;
import cn.hezhaohui.thumb.exception.BusinessException;
import cn.hezhaohui.thumb.exception.ErrorCode;
import cn.hezhaohui.thumb.manager.cache.CacheManager;
import cn.hezhaohui.thumb.model.dto.DoThumbRequest;
import cn.hezhaohui.thumb.model.entity.Blog;
import cn.hezhaohui.thumb.model.entity.User;
import cn.hezhaohui.thumb.service.BlogService;
import cn.hezhaohui.thumb.service.UserService;
import cn.hezhaohui.thumb.util.RedisKeyUtil;
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
*/
@Service("thumbService")
@Slf4j
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

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

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        // Lock: 这里使用字符串加锁，获取字符串常量对象才可运行，字符串常量对象具有唯一性
        synchronized (("LOCK-USERID-" + loginUser.getId().toString()).intern()) {
            // Transaction: 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // 判断是否已点过赞
                Boolean exists = this.hasThumb(blogId, loginUser.getId());
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
                thumb.setUserid(loginUser.getId());
                thumb.setBlogId(blogId);
                // 两者一起执行
                boolean success = update && this.save(thumb);
                // 点赞记录存入 Redis
                if (success) {
//                    一级缓存
//                    redisTemplate.opsForHash().put(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString(), thumb.getId());
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String filedKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, filedKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, filedKey, realThumbId);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        // Lock: 这里使用字符串加锁，获取字符串常量对象才可运行，字符串常量对象具有唯一性
        synchronized (("LOCK-USERID-" + loginUser.getId().toString()).intern()) {
            // Transaction: 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                // 判断是否已点过赞
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
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
                // 两者一起执行
                boolean success = update && this.removeById(((Number)thumbIdObj).longValue());
                if (success) {
//                    一级缓存
//                    redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
//        一级缓存
//        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());


        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());;
        if (thumbIdObj == null) {
            return false;
        }
        Long thumbId = ((Number) thumbIdObj).longValue();
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);

    }
}




