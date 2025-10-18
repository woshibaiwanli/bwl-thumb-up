package cn.hezhaohui.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hezhaohui.thumb.model.entity.User;
import cn.hezhaohui.thumb.service.UserService;
import cn.hezhaohui.thumb.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author 23117
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-10-18 14:51:07
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




