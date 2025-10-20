package cn.hezhaohui.thumb.constent;


/**
 * 点赞常量接口
 * 用于定义与点赞功能相关的常量
 */
public interface ThumbConstant {

    /**
     * 用户点赞前缀常量
     * 用于作为Redis中存储用户点赞信息的key前缀
     */
    String USER_THUMB_KEY_PREFIX = "thumb:";
}
