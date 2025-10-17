# 创建数据库
CREATE DATABASE IF NOT EXISTS thumb_up;

# 切换数据库
USE thumb_up;

# 用户表
CREATE TABLE IF NOT EXISTS user
(
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    username    VARCHAR(128)    NOT NULL COMMENT '用户名'
);

# 博客表
CREATE TABLE IF NOT EXISTS blog
(
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    userId      BIGINT      NOT NULL COMMENT '用户 id',
    title       VARCHAR(512)    NULL COMMENT '标题',
    coverImg    VARCHAR(1024)   NULL COMMENT '封面',
    content     TEXT        NOT NULL COMMENT '内容',
    thumbCount  INT         DEFAULT 0 NOT NULL COMMENT '点赞数',
    createTime  DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
);
CREATE INDEX idx_userId ON blog(userId);

# 点赞记录表
CREATE TABLE IF NOT EXISTS thumb
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'id',
    userId          BIGINT NOT NULL COMMENT '用户 id',
    blogId          BIGINT NOT NULL COMMENT '博客 id',
    createTime      DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间'
);
CREATE UNIQUE INDEX idx_userId_blogId ON thumb (userId, blogId) ;