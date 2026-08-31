package cn.iocoder.yudao.module.eam.enums.transfer;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

/**
 * EAM 流转类型枚举
 */
@Getter
@AllArgsConstructor
public enum EamTransferTypeEnum implements ArrayValuable<Integer> {

    RECEIVE(1, "领用"),
    RETURN(2, "退还"),
    BORROW(3, "借用"),
    GIVE_BACK(4, "归还"),
    ALLOCATE(5, "调拨");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamTransferTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 需要 BPM 审批的流转类型
     */
    public static final Set<Integer> NEED_APPROVAL = Set.of(
            RECEIVE.type, BORROW.type, ALLOCATE.type);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
