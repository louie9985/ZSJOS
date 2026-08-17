package cn.iocoder.yudao.module.eam.enums.category;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum EamManagementModeEnum implements ArrayValuable<Integer> {

    SERIALIZED(1, "单件"),
    BATCH(2, "批量");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamManagementModeEnum::getMode).toArray(Integer[]::new);

    private final Integer mode;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isBatch(Integer mode) {
        return BATCH.mode.equals(mode);
    }

}
