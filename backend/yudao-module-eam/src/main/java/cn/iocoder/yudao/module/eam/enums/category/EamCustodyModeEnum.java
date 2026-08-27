package cn.iocoder.yudao.module.eam.enums.category;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum EamCustodyModeEnum implements ArrayValuable<Integer> {

    CONSUMABLE(1, "消耗型"),
    RETURNABLE(2, "需归还型");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamCustodyModeEnum::getMode).toArray(Integer[]::new);

    private final Integer mode;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
