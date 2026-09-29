package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetChangeLogMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.stock.EamStockHoldingMapper;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EamAssetStatusPersistenceTest extends BaseDbUnitTest {

    @Resource private EamAssetMapper assetMapper;
    @Resource private EamAssetChangeLogMapper changeLogMapper;
    private EamAssetServiceImpl service;

    @BeforeEach
    void setUpService() {
        EamAssetChangeLogServiceImpl changeLogService = new EamAssetChangeLogServiceImpl();
        ReflectionTestUtils.setField(changeLogService, "changeLogMapper", changeLogMapper);
        service = new EamAssetServiceImpl();
        ReflectionTestUtils.setField(service, "assetMapper", assetMapper);
        ReflectionTestUtils.setField(service, "changeLogService", changeLogService);
        ReflectionTestUtils.setField(service, "stockHoldingMapper", mock(EamStockHoldingMapper.class));
    }

    @ParameterizedTest
    @EnumSource(EamAssetStatusEnum.class)
    void directCorrectionShouldPersistStatusWithoutErasingUsage(EamAssetStatusEnum target) {
        EamAssetDO before = asset().setUseEmployeeId(30L).setUseDeptId(40L)
                .setUseEmployeeNameSnapshot("员工快照");
        assetMapper.insert(before);

        service.changeStatus(before.getId(), target.getStatus(), "盘点纠正", 99L);

        EamAssetDO after = assetMapper.selectById(before.getId());
        assertEquals(target.getStatus(), after.getStatus());
        assertEquals(4, after.getVersion());
        assertEquals(30L, after.getUseEmployeeId());
        assertEquals(40L, after.getUseDeptId());
        assertEquals("员工快照", after.getUseEmployeeNameSnapshot());
        assertEquals("原存放地点", after.getLocation());
        assertEquals("99", after.getUpdater());
        var logs = changeLogMapper.selectListByAssetId(before.getId());
        assertEquals(1, logs.size());
        var log = logs.get(0);
        assertEquals(0, log.getBeforeStatus());
        assertEquals(target.getStatus(), log.getAfterStatus());
        assertEquals(30L, log.getBeforeEmployeeId());
        assertEquals(30L, log.getAfterEmployeeId());
        assertEquals(40L, log.getBeforeDeptId());
        assertEquals(40L, log.getAfterDeptId());
        assertEquals(99L, log.getOperatorId());
    }

    @Test
    void directCorrectionShouldPreserveUnmatchedHistoricalEmployeeSnapshot() {
        EamAssetDO before = asset().setUseEmployeeNameSnapshot("历史员工快照");
        assetMapper.insert(before);

        service.changeStatus(before.getId(), EamAssetStatusEnum.IN_USE.getStatus(), null, 99L);

        EamAssetDO after = assetMapper.selectById(before.getId());
        assertNull(after.getUseEmployeeId());
        assertNull(after.getUseDeptId());
        assertEquals("历史员工快照", after.getUseEmployeeNameSnapshot());
    }

    @Test
    void explicitClearUsageShouldStillRemoveAssignmentAndSnapshot() {
        EamAssetDO before = asset().setStatus(EamAssetStatusEnum.IN_USE.getStatus())
                .setUseEmployeeId(30L).setUseDeptId(40L).setUseEmployeeNameSnapshot("员工快照");
        assetMapper.insert(before);

        service.clearUsageAndSetIdle(before.getId(), 3, 99L);

        EamAssetDO after = assetMapper.selectById(before.getId());
        assertEquals(EamAssetStatusEnum.IDLE.getStatus(), after.getStatus());
        assertEquals(4, after.getVersion());
        assertNull(after.getUseEmployeeId());
        assertNull(after.getUseDeptId());
        assertNull(after.getUseEmployeeNameSnapshot());
    }

    private EamAssetDO asset() {
        return new EamAssetDO().setAssetCode("EAM-STATUS-TEST").setName("回归测试资产")
                .setCategoryId(20L).setStatus(EamAssetStatusEnum.IDLE.getStatus())
                .setVersion(3).setLocation("原存放地点");
    }
}
