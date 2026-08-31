package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CATEGORY_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadCategorySnapshotServiceTest {

    @InjectMocks
    private LeadCategorySnapshotService service;
    @Mock
    private DictDataApi dictDataApi;

    @Test
    void requireEnabledReturnsStableValueAndCurrentLabel() {
        DictDataRespDTO category = new DictDataRespDTO();
        category.setValue("high_intent");
        category.setLabel("高意向");
        category.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_lead_category")).thenReturn(List.of(category));

        LeadCategorySnapshotService.Selection result = service.requireEnabled(" high_intent ");

        assertEquals("high_intent", result.value());
        assertEquals("高意向", result.labelSnapshot());
    }

    @Test
    void requireEnabledRejectsDisabledCategory() {
        DictDataRespDTO category = new DictDataRespDTO();
        category.setValue("legacy");
        category.setLabel("旧分类");
        category.setStatus(1);
        when(dictDataApi.getDictDataList("zsjos_lead_category")).thenReturn(List.of(category));

        ServiceException error = assertThrows(ServiceException.class, () -> service.requireEnabled("legacy"));

        assertEquals(LEAD_CATEGORY_INVALID.getCode(), error.getCode());
    }
}
