package cn.hezhaohui.thumb.model.enums;

import lombok.Getter;

@Getter
public enum LuaStatusEnum {
    // 成功
    SUCCESS(1L),
    // 失败
    FAIL(0L);

    private final long value;

    LuaStatusEnum(long value) {
        this.value = value;
    }
}
