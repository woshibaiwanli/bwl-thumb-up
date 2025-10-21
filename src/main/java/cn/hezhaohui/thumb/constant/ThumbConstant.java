package cn.hezhaohui.thumb.constant;


/**
 * 点赞常量接口
 * 用于定义与点赞功能相关的常量
 */
public class ThumbConstant {

    /**
     * 用户点赞前缀常量
     * 用于作为Redis中存储用户点赞信息的key前缀
     */
    public static final String USER_THUMB_KEY_PREFIX = "thumb:";

    /**
     * 临时点赞前缀常量
     * 用于作为Redis中存储临时点赞信息的key前缀
     */
    public static final String TEMP_THUMB_KEY_PREFIX = "temp_thumb:%s";
}
