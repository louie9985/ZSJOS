package cn.iocoder.yudao.module.system.service.ip;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaSaveReqVO;

import cn.iocoder.yudao.module.system.dal.dataobject.ip.AreaDO;
import cn.iocoder.yudao.module.system.dal.mysql.ip.AreaMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;

@Import(AreaServiceImpl.class)
class AreaServiceImplTest extends BaseDbUnitTest {

    @Resource
    private AreaServiceImpl areaService;
    @Resource
    private AreaMapper areaMapper;

    @Test
    void createDerivesLevelFromParent() {
        insertArea(1, "中国", 1, 0, 0);
        AreaSaveReqVO reqVO = request(110000, "北京市", 1);

        assertEquals(110000, areaService.createArea(reqVO));
        assertEquals(2, areaMapper.selectById(110000).getType());
    }

    @Test
    void createRejectsDuplicateCodeAndName() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);

        assertServiceException(() -> areaService.createArea(request(110000, "新北京", 1)), AREA_CODE_DUPLICATE);
        assertServiceException(() -> areaService.createArea(request(120000, "北京市", 1)), AREA_NAME_DUPLICATE);
    }

    @Test
    void updateRejectsDescendantAsParent() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);
        insertArea(110100, "北京市", 3, 110000, 0);

        assertServiceException(() -> areaService.updateArea(request(110000, "北京市", 110100)), AREA_PARENT_INVALID);
    }

    @Test
    void enabledTreeExcludesDisabledSubtreeButHistoricalReadRemains() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 1);
        insertArea(110100, "北京市", 3, 110000, 0);

        assertTrue(areaService.getEnabledChinaTree().isEmpty());
        assertEquals("北京市 北京市", areaService.format(110100));
        assertNotNull(areaService.getArea(110100));
    }

    @Test
    void updateMoveRecalculatesDescendantLevels() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);
        insertArea(110100, "北京城区", 3, 110000, 0);
        insertArea(110101, "东城区", 4, 110100, 0);

        areaService.updateArea(request(110100, "北京城区", 1));

        assertEquals(2, areaMapper.selectById(110100).getType());
        assertEquals(3, areaMapper.selectById(110101).getType());
    }

    @Test
    void updateMoveRejectsSubtreeBeyondDistrictLevel() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);
        insertArea(110100, "北京市", 3, 110000, 0);
        insertArea(110101, "东城区", 4, 110100, 0);
        insertArea(120000, "天津市", 2, 1, 0);
        insertArea(120100, "天津市", 3, 120000, 0);

        assertServiceException(() -> areaService.updateArea(request(110100, "北京市", 120100)), AREA_LEVEL_EXCEEDED);
        assertEquals(110000, areaMapper.selectById(110100).getParentId());
        assertEquals(4, areaMapper.selectById(110101).getType());
    }

    @Test
    void createRejectsDirectSelectionOutsideProvinceLevel() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);
        AreaSaveReqVO reqVO = request(110100, "北京市", 110000);
        reqVO.setLeafSelectable(true);

        assertServiceException(() -> areaService.createArea(reqVO), AREA_LEAF_SELECTABLE_INVALID);
    }

    @Test
    void treeReturnsConfiguredSelectionCodeAndDirectLeafFlag() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(810000, "香港特别行政区", 2, 1, 0);
        AreaDO hongKong = areaMapper.selectById(810000);
        hongKong.setSelectionCode("810000");
        hongKong.setLeafSelectable(true);
        areaMapper.updateById(hongKong);

        var node = areaService.getEnabledChinaTree().getFirst();

        assertEquals("810000", node.getSelectionCode());
        assertTrue(node.getLeafSelectable());
    }

    @Test
    void treeKeepsOtherFirstAtEveryLevelRegardlessOfStoredSort() {
        insertOrderingFixture();

        var provinces = areaService.getEnabledChinaTree();

        assertEquals(List.of(990000000, 120000, 110000),
                provinces.stream().map(AreaNodeRespVO::getId).toList());
        var cities = provinces.getLast().getChildren();
        assertEquals(List.of(990000001, 110100, 110200),
                cities.stream().map(AreaNodeRespVO::getId).toList());
        assertEquals(List.of(990000002, 110101),
                cities.get(1).getChildren().stream().map(AreaNodeRespVO::getId).toList());
    }

    @Test
    void managementListsKeepOtherFirstAndPreserveOrdinarySortAndIdOrder() {
        insertOrderingFixture();
        List<Integer> expected = List.of(1, 990000000, 120000, 110000,
                990000001, 110100, 110200, 990000002, 110101);

        assertEquals(expected, areaService.getAreaList().stream().map(AreaDO::getId).toList());
        assertEquals(expected, areaService.getAreaList(new AreaListReqVO()).stream()
                .map(AreaDO::getId).toList());
        AreaListReqVO filter = new AreaListReqVO();
        filter.setName("测试城市");
        assertEquals(List.of(990000001, 110100, 110200),
                areaService.getAreaList(filter).stream().map(AreaDO::getId).toList());
    }

    private void insertOrderingFixture() {
        insertArea(1, "中国", 1, 0, 0);
        insertArea(110000, "北京市", 2, 1, 0);
        insertArea(120000, "天津市", 2, 1, 0);
        AreaDO tianjin = areaMapper.selectById(120000);
        tianjin.setSort(0);
        areaMapper.updateById(tianjin);
        insertArea(110200, "测试城市二", 3, 110000, 0);
        insertArea(110100, "测试城市一", 3, 110000, 0);
        insertArea(110101, "东城区", 4, 110100, 0);
        insertOtherArea(990000000, "其他", 2, 1);
        insertOtherArea(990000001, "其他测试城市", 3, 110000);
        insertOtherArea(990000002, "其他区县", 4, 110100);
    }

    private void insertOtherArea(int id, String name, int type, int parentId) {
        insertArea(id, name, type, parentId, 0);
        AreaDO other = areaMapper.selectById(id);
        other.setSelectionCode("OTHER");
        other.setSort(Integer.MAX_VALUE);
        areaMapper.updateById(other);
    }

    private static AreaSaveReqVO request(int id, String name, int parentId) {
        AreaSaveReqVO reqVO = new AreaSaveReqVO();
        reqVO.setId(id);
        reqVO.setName(name);
        reqVO.setParentId(parentId);
        reqVO.setSort(1);
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setLeafSelectable(false);
        return reqVO;
    }

    private void insertArea(int id, String name, int type, int parentId, int status) {
        AreaDO area = new AreaDO();
        area.setId(id);
        area.setName(name);
        area.setSelectionCode(String.valueOf(id));
        area.setType(type);
        area.setParentId(parentId);
        area.setSort(1);
        area.setStatus(status);
        area.setLeafSelectable(false);
        areaMapper.insert(area);
    }

}
