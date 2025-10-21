package cn.hezhaohui.thumb.service.impl;

import cn.hezhaohui.thumb.constent.ThumbConstant;
import cn.hezhaohui.thumb.exception.BusinessException;
import cn.hezhaohui.thumb.exception.ErrorCode;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
* @author 23117
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-10-18 14:51:00
*/
@Service
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
                    redisTemplate.opsForHash().put(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString(), thumb.getId());
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
                Object thumbIdObj = redisTemplate.opsForHash().get(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                if (thumbIdObj == null) {
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
                boolean success = update && this.removeById(thumbId);
                if (success) {
                    redisTemplate.opsForHash().delete(RedisKeyUtil.getUserThumbKey(loginUser.getId()), blogId.toString());
                }
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }
}




