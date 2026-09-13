package cn.baiwanli.thumb.service.impl;

import cn.baiwanli.thumb.constant.UserConstant;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.baiwanli.thumb.model.entity.User;
import cn.baiwanli.thumb.service.UserService;
import cn.baiwanli.thumb.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author 23117
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-10-18 14:51:07
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{


    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }
}




