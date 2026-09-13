package cn.baiwanli.thumb.service;

import cn.baiwanli.thumb.model.entity.Blog;
import cn.baiwanli.thumb.model.vo.BlogVO;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author 23117
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-10-18 14:50:16
*/
public interface BlogService extends IService<Blog> {


    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
