package cn.iocoder.yudao.module.eam.service.procurement;

import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamDemandItemReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockCandidateRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.procurement.EamDemandItemDO;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandItemMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.procurement.EamDemandMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.framework.approval.EamApprovalService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryPolicy;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.stock.EamStockService;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EamDemandServiceImplTest {

    @InjectMocks
    private EamDemandServiceImpl service;
    @Mock
    private EamDemandMapper demandMapper;
    @Mock
    private EamDemandItemMapper itemMapper;
    @Mock
    private EamCategoryService categoryService;
    @Mock
    private EamCategoryFieldService fieldService;
    @Mock
    private EamStockService stockService;
    @Mock
    private EamApprovalService approvalService;
    @Mock
    private HrmEmployeeApi employeeApi;

    @Test
    void previewCandidates_shouldUseEffectiveCategoryPolicyAndNormalizedFields() {
        EamDemandItemReqVO input = new EamDemandItemReqVO();
        input.setName("笔记本电脑");
        input.setCategoryId(10L);
        input.setQuantity(2);
        input.setExtFields(Map.of("memory", "32G"));
        EamCategoryDO category = new EamCategoryDO();
        category.setId(10L);
        category.setManagementMode(EamManagementModeEnum.SERIALIZED.getMode());
        category.setUnit("台");
        when(categoryService.validateCategoryExists(10L)).thenReturn(category);
        when(categoryService.getEffectivePolicy(10L)).thenReturn(new EamCategoryPolicy(1, 2));
        when(fieldService.validateAndNormalizeExtFieldsWithSnapshots(10L, input.getExtFields()))
                .thenReturn(new EamCategoryFieldService.NormalizedExtFields(
                        Map.of("memory", "32G"), Map.of(), Map.of()));
        EamStockCandidateRespVO candidate = new EamStockCandidateRespVO();
        candidate.setAssetId(20L);
        when(stockService.getCandidates(org.mockito.ArgumentMatchers.any(EamDemandItemDO.class)))
                .thenReturn(List.of(candidate));

        List<EamStockCandidateRespVO> result = service.previewCandidates(input);

        assertEquals(List.of(candidate), result);
        ArgumentCaptor<EamDemandItemDO> captor = ArgumentCaptor.forClass(EamDemandItemDO.class);
        verify(stockService).getCandidates(captor.capture());
        assertEquals(EamManagementModeEnum.SERIALIZED.getMode(), captor.getValue().getManagementMode());
        assertEquals(1, captor.getValue().getDeliveryMode());
        assertEquals(2, captor.getValue().getCustodyMode());
        assertEquals("台", captor.getValue().getUnit());
        assertEquals(Map.of("memory", "32G"), captor.getValue().getExtFields());
    }

}
