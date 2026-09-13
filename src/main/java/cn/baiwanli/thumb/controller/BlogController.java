package cn.baiwanli.thumb.controller;

import cn.baiwanli.thumb.common.BaseResponse;
import cn.baiwanli.thumb.common.ResultUtils;
import cn.baiwanli.thumb.model.entity.Blog;
import cn.baiwanli.thumb.model.vo.BlogVO;
import cn.baiwanli.thumb.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("blog")
public class BlogController {
    @Resource
    private BlogService blogService;

    @GetMapping("/get")
    public BaseResponse<BlogVO> get(long blogId, HttpServletRequest request) {
        BlogVO blogVO = blogService.getBlogVOById(blogId, request);
        return ResultUtils.sucess(blogVO);
    }

    @GetMapping("list")
    public BaseResponse<List<BlogVO>> list(HttpServletRequest request) {
        List<Blog> blogList = blogService.list();
        List<BlogVO> blogVOList = blogService.getBlogVOList(blogList, request);
        return ResultUtils.sucess(blogVOList);
    }
}
