package cn.iocoder.yudao.module.eam.enums.asset;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * EAM 资产变更类型枚举
 */
@Getter
@AllArgsConstructor
public enum EamChangeTypeEnum implements ArrayValuable<Integer> {

    CREATE(0, "创建"),
    EDIT(1, "编辑"),
    RECEIVE(2, "领用"),
    RETURN(3, "退还"),
    BORROW(4, "借用"),
    GIVE_BACK(5, "归还"),
    TRANSFER(6, "调拨"),
    REPAIR(7, "维修"),
    REPAIR_DONE(8, "维修完成"),
    SCRAP_APPLY(9, "申请报废"),
    SCRAP_APPROVE(10, "报废通过"),
    SCRAP_REJECT(11, "报废驳回"),
    INVENTORY(12, "盘点"),
    LOST(13, "标记丢失"),
    FREEZE(14, "冻结"),
    UNFREEZE(15, "解冻");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamChangeTypeEnum::getType).toArray(Integer[]::new);

    private final Integer type;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
