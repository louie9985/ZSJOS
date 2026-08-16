package cn.iocoder.yudao.module.eam.enums.asset;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * EAM 资产状态枚举（八态）
 */
@Getter
@AllArgsConstructor
public enum EamAssetStatusEnum implements ArrayValuable<Integer> {

    IDLE(0, "闲置"),
    IN_USE(1, "在用"),
    LENT(2, "借出"),
    REPAIRING(3, "维修中"),
    PENDING_SCRAP(4, "待报废"),
    SCRAPPED(5, "已报废"),
    LOST(6, "已丢失"),
    FROZEN(7, "已冻结");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(EamAssetStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    // ========== 状态迁移表 ==========

    /**
     * 领用：闲置 → 在用
     */
    public static final Set<Integer> ALLOW_RECEIVE = Set.of(IDLE.status);
    /**
     * 退还：在用 → 闲置
     */
    public static final Set<Integer> ALLOW_RETURN = Set.of(IN_USE.status);
    /**
     * 借用：闲置 → 借出
     */
    public static final Set<Integer> ALLOW_BORROW = Set.of(IDLE.status);
    /**
     * 归还：借出 → 闲置
     */
    public static final Set<Integer> ALLOW_GIVE_BACK = Set.of(LENT.status);
    /**
     * 调拨：在用 → 在用（换人/换部门）
     */
    public static final Set<Integer> ALLOW_TRANSFER = Set.of(IN_USE.status);
    /**
     * 送修：闲置、在用、借出均可送修
     */
    public static final Set<Integer> ALLOW_REPAIR = Set.of(IDLE.status, IN_USE.status, LENT.status);
    /**
     * 维修完成：维修中 → 恢复前状态
     */
    public static final Set<Integer> ALLOW_REPAIR_DONE = Set.of(REPAIRING.status);
    /**
     * 申请报废：除已报废外都允许
     */
    public static final Set<Integer> ALLOW_SCRAP_APPLY = Set.of(
            IDLE.status, IN_USE.status, LENT.status, REPAIRING.status, LOST.status, FROZEN.status);
    /**
     * 报废通过
     */
    public static final Set<Integer> ALLOW_SCRAP_APPROVE = Set.of(PENDING_SCRAP.status);
    /**
     * 标记丢失：除已报废外都允许
     */
    public static final Set<Integer> ALLOW_LOST = Set.of(
            IDLE.status, IN_USE.status, LENT.status, REPAIRING.status, FROZEN.status);
    /**
     * 冻结：除已报废和已冻结外都允许
     */
    public static final Set<Integer> ALLOW_FREEZE = Set.of(
            IDLE.status, IN_USE.status, LENT.status, REPAIRING.status, PENDING_SCRAP.status, LOST.status);
    /**
     * 解冻：已冻结 → 需配合记录恢复到冻结前状态
     */
    public static final Set<Integer> ALLOW_UNFREEZE = Set.of(FROZEN.status);

    /**
     * 终态集合（不允许进一步操作）
     */
    public static final Set<Integer> TERMINAL_STATUSES = Set.of(SCRAPPED.status);

}
