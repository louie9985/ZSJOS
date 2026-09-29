package cn.iocoder.yudao.module.eam.controller.admin.asset;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EamAssetControllerTest {

    @InjectMocks private EamAssetController controller;
    @Mock private EamAssetService assetService;
    @Mock private EamCategoryService categoryService;
    @Mock private HrmEmployeeApi employeeApi;
    @Mock private DeptApi deptApi;

    @ParameterizedTest
    @CsvSource({",", "30,", ",40", "30,40"})
    void detailShouldSupportOptionalUsageAssociations(Long employeeId, Long deptId) {
        EamAssetDO asset = asset().setUseEmployeeId(employeeId).setUseDeptId(deptId);
        when(assetService.getAsset(eq(10L), nullable(Long.class))).thenReturn(asset);
        when(categoryService.getCategoryList()).thenReturn(List.of(category()));
        if (employeeId != null) {
            when(employeeApi.getEmployeeList(Set.of(employeeId))).thenReturn(
                    List.of(new HrmEmployeeRespDTO().setId(employeeId).setName("当前员工")));
        }
        if (deptId != null) {
            when(deptApi.getDeptMap(Set.of(deptId))).thenReturn(
                    Map.of(deptId, new DeptRespDTO().setId(deptId).setName("当前部门")));
        }

        var result = controller.getAsset(10L).getData();

        assertEquals(employeeId == null ? "历史员工" : "当前员工", result.getUseEmployeeName());
        assertEquals(deptId == null ? null : "当前部门", result.getUseDeptName());
        assertEquals("测试分类", result.getCategoryName());
        if (employeeId == null) verifyNoInteractions(employeeApi);
        if (deptId == null) verifyNoInteractions(deptApi);
    }

    @Test
    void detailShouldRetainSnapshotWhenAssociatedRecordsAreMissing() {
        when(assetService.getAsset(eq(10L), nullable(Long.class)))
                .thenReturn(asset().setUseEmployeeId(30L).setUseDeptId(40L));
        when(categoryService.getCategoryList()).thenReturn(List.of(category()));
        when(employeeApi.getEmployeeList(Set.of(30L))).thenReturn(List.of());
        when(deptApi.getDeptMap(Set.of(40L))).thenReturn(Map.of());

        var result = controller.getAsset(10L).getData();

        assertEquals("历史员工", result.getUseEmployeeName());
        assertNull(result.getUseDeptName());
    }

    @Test
    void pageShouldSupportAssetsWithoutAnyUsageAssociations() {
        EamAssetPageReqVO request = new EamAssetPageReqVO();
        when(assetService.getAssetPage(eq(request), nullable(Long.class)))
                .thenReturn(new PageResult<>(List.of(asset()), 1L));
        when(categoryService.getCategoryList()).thenReturn(List.of(category()));

        var result = controller.getAssetPage(request).getData();

        assertEquals(1L, result.getTotal());
        assertEquals("历史员工", result.getList().get(0).getUseEmployeeName());
        assertNull(result.getList().get(0).getUseDeptName());
        verifyNoInteractions(employeeApi, deptApi);
    }

    @Test
    void inaccessibleAssetShouldRemainAbsent() {
        assertNull(controller.getAsset(10L).getData());
        verifyNoInteractions(categoryService, employeeApi, deptApi);
    }

    private EamAssetDO asset() {
        return new EamAssetDO().setId(10L).setCategoryId(20L).setStatus(0)
                .setUseEmployeeNameSnapshot("历史员工");
    }

    private EamCategoryDO category() {
        return new EamCategoryDO().setId(20L).setName("测试分类");
    }
}
