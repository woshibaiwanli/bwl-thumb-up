package cn.hezhaohui.thumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hezhaohui.thumb.model.entity.Blog;
import cn.hezhaohui.thumb.service.BlogService;
import cn.hezhaohui.thumb.mapper.BlogMapper;
import org.springframework.stereotype.Service;

/**
* @author 23117
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-10-18 14:50:16
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

}




