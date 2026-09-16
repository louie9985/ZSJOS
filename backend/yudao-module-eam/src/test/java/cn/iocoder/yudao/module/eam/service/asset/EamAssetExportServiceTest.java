package cn.iocoder.yudao.module.eam.service.asset;

import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetRespVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EamAssetExportServiceTest {
    @InjectMocks private EamAssetExportService service;
    @Mock private EamCategoryFieldService fields;
    @Test
    void exportShouldUseCustomSerialAndHistoricalDictionaryLabel() throws Exception {
        EamCategoryFieldDO sn = new EamCategoryFieldDO().setFieldKey("sn").setFieldName("序列号");
        EamCategoryFieldDO color = new EamCategoryFieldDO().setFieldKey("color").setFieldName("颜色");
        when(fields.getEffectiveFieldList(1L)).thenReturn(List.of(sn, color));
        EamAssetRespVO asset = new EamAssetRespVO();
        asset.setCategoryId(1L);
        asset.setExtFields(Map.of("sn", "SN-001", "color", "black"));
        asset.setExtFieldLabels(Map.of("color", "历史黑色"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.write(response, List.of(asset));
        List<Map<Integer, String>> rows = FastExcelFactory.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .headRowNumber(0).sheet().doReadSync();
        assertEquals("sn:序列号", rows.get(0).get(14));
        assertEquals("SN-001", rows.get(1).get(14));
        assertEquals("历史黑色", rows.get(1).get(15));
        assertFalse(rows.get(0).containsValue("品牌型号"));
        assertFalse(rows.get(0).containsValue("原值"));
    }
}
