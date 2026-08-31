package cn.iocoder.yudao.module.eam.enums.inventory;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * EAM 盘点结果枚举
 */
@Getter
@AllArgsConstructor
public enum EamInventoryResultEnum implements ArrayValuable<Integer> {

    UNCHECKED(0, "未盘"),
    NORMAL(1, "正常"),
    LOCATION_MISMATCH(2, "位置不符"),
    NOT_FOUND(3, "未找到");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamInventoryResultEnum::getResult).toArray(Integer[]::new);

    private final Integer result;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
