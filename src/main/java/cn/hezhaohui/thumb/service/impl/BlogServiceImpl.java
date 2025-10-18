package cn.hezhaohui.thumb.service.impl;

import cn.hezhaohui.thumb.model.entity.Thumb;
import cn.hezhaohui.thumb.model.entity.User;
import cn.hezhaohui.thumb.model.vo.BlogVO;
import cn.hezhaohui.thumb.service.ThumbService;
import cn.hezhaohui.thumb.service.UserService;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hezhaohui.thumb.model.entity.Blog;
import cn.hezhaohui.thumb.service.BlogService;
import cn.hezhaohui.thumb.mapper.BlogMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
* @author 23117
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-10-18 14:50:16
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    public BlogServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    private BlogVO getBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if (loginUser == null) {
            return blogVO;
        }

        Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getUserid, loginUser.getId())
                .eq(Thumb::getBlogId, blog.getId())
                .one();
        blogVO.setHasThumb(thumb != null);

        return blogVO;
    }
}




