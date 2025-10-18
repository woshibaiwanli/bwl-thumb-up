package cn.hezhaohui.thumb.controller;

import cn.hezhaohui.thumb.common.BaseResponse;
import cn.hezhaohui.thumb.common.ResultUtils;
import cn.hezhaohui.thumb.model.vo.BlogVO;
import cn.hezhaohui.thumb.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
