package cn.baiwanli.thumb.service.impl;

import cn.baiwanli.thumb.constant.RedisLuaScriptConstant;
import cn.baiwanli.thumb.exception.BusinessException;
import cn.baiwanli.thumb.exception.ErrorCode;
import cn.baiwanli.thumb.mapper.ThumbMapper;
import cn.baiwanli.thumb.model.dto.DoThumbRequest;
import cn.baiwanli.thumb.model.entity.Blog;
import cn.baiwanli.thumb.model.entity.Thumb;
import cn.baiwanli.thumb.model.entity.User;
import cn.baiwanli.thumb.model.enums.LuaStatusEnum;
import cn.baiwanli.thumb.service.BlogService;
import cn.baiwanli.thumb.service.ThumbService;
import cn.baiwanli.thumb.service.UserService;
import cn.baiwanli.thumb.util.RedisKeyUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

/**
* @author 23117
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-10-18 14:51:00
*/
@Service("thumbServiceRedis")
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        String timeSlice = this.getTimeSlice();
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        // 执行 Lua 脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                // local tempThumbKey = KEYS[1]       -- 临时计数键（如 thumb:temp:{timeSlice}）
                // local userThumbKey = KEYS[2]       -- 用户点赞状态键（如 thumb:{userId}）
                Arrays.asList(tempThumbKey, userThumbKey),
                // local userId = ARGV[1]             -- 用户 ID
                loginUser.getId(),
                // local blogId = ARGV[2]             -- 博客 ID
                blogId
        );
        // 成功 SUCCESS(1L),
        // 失败 FAIL(0L);
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户已点赞");
        }

        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数异常");
        }
        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        String timeSlice = this.getTimeSlice();
        // Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        // 执行 Lua 脚本
        long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                // local tempThumbKey = KEYS[1]       -- 临时计数键（如 thumb:temp:{timeSlice}）
                // local userThumbKey = KEYS[2]       -- 用户点赞状态键（如 thumb:{userId}）
                Arrays.asList(tempThumbKey, userThumbKey),
                // local userId = ARGV[1]             -- 用户 ID
                loginUser.getId(),
                // local blogId = ARGV[2]             -- 博客 ID
                blogId
        );
        // 成功 SUCCESS(1L),
        // 失败 FAIL(0L);
        if (LuaStatusEnum.FAIL.getValue() == result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未点赞");
        }
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
    }

    private String getTimeSlice() {
        DateTime nowDate = DateUtil.date();
        return DateUtil.format(nowDate, "HH:mm:" + (DateUtil.second(nowDate) / 10) * 10);
    }
}




