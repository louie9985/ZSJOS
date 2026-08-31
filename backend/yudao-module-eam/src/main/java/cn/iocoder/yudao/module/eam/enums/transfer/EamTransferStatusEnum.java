package cn.iocoder.yudao.module.eam.enums.transfer;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * EAM 流转单状态枚举
 */
@Getter
@AllArgsConstructor
public enum EamTransferStatusEnum implements ArrayValuable<Integer> {

    APPROVING(0, "审批中"),
    APPROVED(1, "已生效"),
    REJECTED(2, "已驳回"),
    CANCELLED(3, "已取消"),
    DRAFT(4, "草稿"),
    PENDING_INSPECTION(5, "待验收"),
    COMPLETED(6, "已完成"),
    EXCEPTION(7, "异常待处理");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamTransferStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
