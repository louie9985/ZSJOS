package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportPreviewRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetImportRowDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetImportRowMapper;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.EamAssetMapper;
import cn.iocoder.yudao.module.eam.enums.category.EamManagementModeEnum;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EamAssetLedgerImportServiceImplTest {

    @InjectMocks
    private EamAssetLedgerImportServiceImpl importService;
    @Mock
    private EamAssetLedgerParser parser;
    @Mock
    private EamCategoryService categoryService;
    @Mock
    private EamAssetMapper assetMapper;
    @Mock
    private EamAssetImportRowMapper importRowMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private EamCategoryFieldService categoryFieldService;
    @Mock
    private DictDataApi dictDataApi;

    @Test
    void preview_shouldHandleDuplicateExistingAndUserMatchWithoutDroppingRows() {
        EamAssetLedgerParser.LedgerRow repeated = row(3, "", "张三");
        EamAssetLedgerParser.LedgerRow existing = row(4, "ZSJ-100", "未入职姓名");
        when(parser.parse(any(byte[].class))).thenReturn(List.of(repeated, existing));
        EamAssetImportRowDO imported = new EamAssetImportRowDO();
        imported.setRowNum(3);
        when(importRowMapper.selectMapByFile(anyString(), anyString())).thenReturn(Map.of(3, imported));

        EamAssetDO existingAsset = new EamAssetDO();
        existingAsset.setId(100L);
        existingAsset.setAssetCode("ZSJ-100");
        when(assetMapper.selectListByAssetCodes(anyList())).thenReturn(List.of(existingAsset));
        when(categoryService.getCategoryList()).thenReturn(categories());
        when(adminUserApi.getUserListByStatus(0)).thenReturn(List.of(user(1L, "张三", 10L)));
        when(categoryFieldService.validateAndNormalizeExtFieldsWithSnapshots(any(), any()))
                .thenReturn(new EamCategoryFieldService.NormalizedExtFields(Map.of(), Map.of(), Map.of()));
        EamAssetImportPreviewRespVO defaultPreview = importService.preview(
                new byte[]{1, 2, 3}, "台账.xlsx", false);
        EamAssetImportPreviewRespVO updatePreview = importService.preview(
                new byte[]{1, 2, 3}, "台账.xlsx", true);

        assertEquals(2, defaultPreview.getTotalRows());
        assertEquals(2, defaultPreview.getSkipCount());
        assertEquals("SKIP_SAME_FILE", defaultPreview.getRows().get(0).getAction());
        assertEquals("张三", defaultPreview.getRows().get(0).getMatchedUserName());
        assertEquals("SKIP_EXISTING", defaultPreview.getRows().get(1).getAction());
        assertTrue(defaultPreview.getRows().get(1).getWarnings().stream()
                .anyMatch(message -> message.contains("未匹配系统用户")));
        assertEquals(1, updatePreview.getUpdateCount());
        assertEquals("UPDATE", updatePreview.getRows().get(1).getAction());
    }

    private static EamAssetLedgerParser.LedgerRow row(int rowNum, String code, String userName) {
        return new EamAssetLedgerParser.LedgerRow(rowNum, "IT-COMPUTER", "电脑", code, "",
                "品牌", "SN", "C栋", 1, 0, null, null, userName, null, null, null, null,
                new LinkedHashMap<>(), Map.of("使用人", userName), List.of(), List.of(), List.of());
    }

    private static List<EamCategoryDO> categories() {
        EamCategoryDO root = EamCategoryDO.builder().id(1L).parentId(0L).name("IT硬件设备")
                .code("IT").managementMode(EamManagementModeEnum.SERIALIZED.getMode()).unit("个").build();
        EamCategoryDO leaf = EamCategoryDO.builder().id(2L).parentId(1L).name("电脑")
                .code("IT-COMPUTER").managementMode(EamManagementModeEnum.SERIALIZED.getMode()).unit("台").build();
        return List.of(root, leaf);
    }

    private static AdminUserRespDTO user(Long id, String nickname, Long deptId) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        user.setDeptId(deptId);
        return user;
    }

}
