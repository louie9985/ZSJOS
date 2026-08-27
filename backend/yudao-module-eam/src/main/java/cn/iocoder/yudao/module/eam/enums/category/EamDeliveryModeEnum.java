package cn.iocoder.yudao.module.eam.enums.category;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum EamDeliveryModeEnum implements ArrayValuable<Integer> {

    PHYSICAL(1, "实物入库"),
    DIGITAL(2, "数字交付");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamDeliveryModeEnum::getMode).toArray(Integer[]::new);

    private final Integer mode;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
