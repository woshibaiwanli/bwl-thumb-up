package cn.baiwanli.thumb.controller;

import cn.baiwanli.thumb.common.BaseResponse;
import cn.baiwanli.thumb.common.ResultUtils;
import cn.baiwanli.thumb.constant.UserConstant;
import cn.baiwanli.thumb.model.entity.User;
import cn.baiwanli.thumb.service.UserService;
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
