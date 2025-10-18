package cn.hezhaohui.thumb.controller;

import cn.hezhaohui.thumb.common.BaseResponse;
import cn.hezhaohui.thumb.common.ResultUtils;
import cn.hezhaohui.thumb.model.dto.DoThumbRequest;
import cn.hezhaohui.thumb.service.ThumbService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("thumb")
public class ThumbController {
    @Resource
    private ThumbService thumbService;

    @PostMapping("/do")
    public BaseResponse<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request) {
        Boolean success = thumbService.doThumb(doThumbRequest, request);
        return ResultUtils.sucess(success);
    }
}
