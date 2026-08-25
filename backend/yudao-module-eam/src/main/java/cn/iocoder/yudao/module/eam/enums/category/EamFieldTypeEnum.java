package cn.iocoder.yudao.module.eam.enums.category;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * EAM 自定义字段类型枚举
 */
@Getter
@AllArgsConstructor
public enum EamFieldTypeEnum implements ArrayValuable<Integer> {

    TEXT(1, "单行文本"),
    TEXTAREA(2, "多行文本"),
    NUMBER(3, "数字"),
    DATE(4, "日期"),
    SELECT(5, "下拉选择"),
    FILE(6, "图片/文件");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamFieldTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
