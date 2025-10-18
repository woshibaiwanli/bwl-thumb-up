package cn.hezhaohui.thumb.service;

import cn.hezhaohui.thumb.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 23117
* @description 针对表【user】的数据库操作Service
* @createDate 2025-10-18 14:51:07
*/
public interface UserService extends IService<User> {

    User getLoginUser(HttpServletRequest request);
}
