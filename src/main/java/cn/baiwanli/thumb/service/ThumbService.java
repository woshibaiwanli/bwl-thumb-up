package cn.hezhaohui.thumb.service;

import cn.hezhaohui.thumb.model.dto.DoThumbRequest;
import cn.hezhaohui.thumb.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author 23117
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-10-18 14:51:00
*/
public interface ThumbService extends IService<Thumb> {


    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean hasThumb(Long blogId, Long userId);
}
