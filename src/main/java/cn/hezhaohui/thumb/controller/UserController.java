package cn.hezhaohui.thumb.controller;

import cn.hezhaohui.thumb.common.BaseResponse;
import cn.hezhaohui.thumb.common.ResultUtils;
import cn.hezhaohui.thumb.constant.UserConstant;
import cn.hezhaohui.thumb.model.entity.User;
import cn.hezhaohui.thumb.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserController {
    @Resource
    private UserService userService;

    @GetMapping("/login")
    public BaseResponse<User> login(long userId, HttpServletRequest request) {
        User user = userService.getById(userId);
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);
        return ResultUtils.sucess(user);
    }

    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        return ResultUtils.sucess(userService.getLoginUser(request));
    }
}
