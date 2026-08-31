package cn.iocoder.yudao.module.eam.enums.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EamAssetStatusEnum} 状态迁移表的单元测试
 *
 * 状态机是资产台账的核心约束，这里锁死每个操作允许的起始状态，
 * 防止后续调整枚举时无意放宽了限制。
 */
public class EamAssetStatusEnumTest {

    @Test
    public void testReceive_onlyFromIdle() {
        assertTrue(EamAssetStatusEnum.ALLOW_RECEIVE.contains(EamAssetStatusEnum.IDLE.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_RECEIVE.contains(EamAssetStatusEnum.IN_USE.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_RECEIVE.contains(EamAssetStatusEnum.LENT.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_RECEIVE.contains(EamAssetStatusEnum.SCRAPPED.getStatus()));
    }

    @Test
    public void testReturn_onlyFromInUse() {
        assertTrue(EamAssetStatusEnum.ALLOW_RETURN.contains(EamAssetStatusEnum.IN_USE.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_RETURN.contains(EamAssetStatusEnum.IDLE.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_RETURN.contains(EamAssetStatusEnum.LENT.getStatus()));
    }

    @Test
    public void testBorrowAndGiveBack_arePaired() {
        // 借用从闲置出发，归还从借出回来，两者不能互换
        assertTrue(EamAssetStatusEnum.ALLOW_BORROW.contains(EamAssetStatusEnum.IDLE.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_GIVE_BACK.contains(EamAssetStatusEnum.LENT.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_BORROW.contains(EamAssetStatusEnum.LENT.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_GIVE_BACK.contains(EamAssetStatusEnum.IDLE.getStatus()));
    }

    @Test
    public void testAllocate_onlyFromInUse() {
        assertTrue(EamAssetStatusEnum.ALLOW_TRANSFER.contains(EamAssetStatusEnum.IN_USE.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_TRANSFER.contains(EamAssetStatusEnum.IDLE.getStatus()));
    }

    @Test
    public void testRepair_allowsIdleInUseAndLent() {
        assertTrue(EamAssetStatusEnum.ALLOW_REPAIR.contains(EamAssetStatusEnum.IDLE.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_REPAIR.contains(EamAssetStatusEnum.IN_USE.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_REPAIR.contains(EamAssetStatusEnum.LENT.getStatus()));
        // 已报废资产不能再送修
        assertFalse(EamAssetStatusEnum.ALLOW_REPAIR.contains(EamAssetStatusEnum.SCRAPPED.getStatus()));
    }

    @Test
    public void testScrapApply_excludesAlreadyScrapped() {
        assertFalse(EamAssetStatusEnum.ALLOW_SCRAP_APPLY.contains(
                EamAssetStatusEnum.SCRAPPED.getStatus()));
        // 待报废本身也不应再次发起申请，避免重复单据
        assertFalse(EamAssetStatusEnum.ALLOW_SCRAP_APPLY.contains(
                EamAssetStatusEnum.PENDING_SCRAP.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_SCRAP_APPLY.contains(EamAssetStatusEnum.IDLE.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_SCRAP_APPLY.contains(EamAssetStatusEnum.LOST.getStatus()));
    }

    @Test
    public void testScrapApprove_onlyFromPendingScrap() {
        assertEquals(1, EamAssetStatusEnum.ALLOW_SCRAP_APPROVE.size());
        assertTrue(EamAssetStatusEnum.ALLOW_SCRAP_APPROVE.contains(
                EamAssetStatusEnum.PENDING_SCRAP.getStatus()));
    }

    @Test
    public void testLost_excludesScrapped() {
        assertFalse(EamAssetStatusEnum.ALLOW_LOST.contains(EamAssetStatusEnum.SCRAPPED.getStatus()));
        assertTrue(EamAssetStatusEnum.ALLOW_LOST.contains(EamAssetStatusEnum.IN_USE.getStatus()));
    }

    @Test
    public void testFreezeUnfreeze_arePaired() {
        assertFalse(EamAssetStatusEnum.ALLOW_FREEZE.contains(EamAssetStatusEnum.FROZEN.getStatus()));
        assertFalse(EamAssetStatusEnum.ALLOW_FREEZE.contains(EamAssetStatusEnum.SCRAPPED.getStatus()));
        assertEquals(1, EamAssetStatusEnum.ALLOW_UNFREEZE.size());
        assertTrue(EamAssetStatusEnum.ALLOW_UNFREEZE.contains(EamAssetStatusEnum.FROZEN.getStatus()));
    }

    @Test
    public void testTerminalStatuses() {
        assertTrue(EamAssetStatusEnum.TERMINAL_STATUSES.contains(
                EamAssetStatusEnum.SCRAPPED.getStatus()));
        // 已丢失仍可申请报废，因此不是终态
        assertFalse(EamAssetStatusEnum.TERMINAL_STATUSES.contains(
                EamAssetStatusEnum.LOST.getStatus()));
    }

    @Test
    public void testArraysCoversAllValues() {
        assertEquals(EamAssetStatusEnum.values().length, EamAssetStatusEnum.ARRAYS.length);
    }

}
