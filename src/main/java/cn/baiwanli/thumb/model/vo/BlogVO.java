package cn.baiwanli.thumb.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class BlogVO {

    private Long id;

    private String title;

    private String coverImg;

    private String content;

    private Integer thumbCount;

    private Date createTime;

    private Boolean hasThumb;
}
