package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

class LeadSalesStageSnapshotTest {
    private final DictDataApi dictionaries = mock(DictDataApi.class);
    private DictDataRespDTO entry(String value, String label, int status) {
        var item = new DictDataRespDTO(); item.setValue(value); item.setLabel(label); item.setStatus(status); return item;
    }
    @Test void newLeadUsesEnabledDefaultLabelSnapshot() {
        when(dictionaries.getDictDataList(LeadSalesStageSnapshot.DICT_TYPE))
                .thenReturn(List.of(entry("pending_contact", "管理员初始名称", 0)));
        var lead = new LeadDO(); LeadSalesStageSnapshot.initialize(lead, dictionaries);
        assertEquals("pending_contact", lead.getSalesStage());
        assertEquals("管理员初始名称", lead.getSalesStageLabelSnapshot());
    }
    @Test void missingOrDisabledDefaultHasDedicatedError() {
        for (var items : List.of(List.<DictDataRespDTO>of(), List.of(entry("pending_contact", "停用", 1)))) {
            when(dictionaries.getDictDataList(LeadSalesStageSnapshot.DICT_TYPE)).thenReturn(items);
            assertEquals(LEAD_SALES_STAGE_DEFAULT_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> LeadSalesStageSnapshot.initialize(new LeadDO(), dictionaries)).getCode());
        }
    }
    @Test void unchangedOrOmittedChoiceNeverResolvesCurrentDictionary() {
        var lead = new LeadDO().setSalesStage("contacted").setSalesStageLabelSnapshot("当时的名称");
        assertEquals("当时的名称", LeadSalesStageSnapshot.resolve(lead, "contacted", dictionaries).label());
        assertEquals("当时的名称", LeadSalesStageSnapshot.resolve(lead, null, dictionaries).label());
        assertNull(LeadSalesStageSnapshot.resolve(new LeadDO(), null, dictionaries).label());
        verifyNoInteractions(dictionaries);
    }
    @Test void changeAndRegressionUseNewSelectionSnapshotAndRejectUnavailableValue() {
        when(dictionaries.getDictDataList(LeadSalesStageSnapshot.DICT_TYPE)).thenReturn(List.of(
                entry("pending_contact", "待触达", 0), entry("contacted", "已停用", 1)));
        var lead = new LeadDO().setSalesStage("intent_customer").setSalesStageLabelSnapshot("意向客户");
        assertEquals("待触达", LeadSalesStageSnapshot.resolve(lead, "pending_contact", dictionaries).label());
        for (String value : List.of("contacted", "missing", "")) {
            assertEquals(LEAD_SALES_STAGE_INVALID.getCode(), assertThrows(ServiceException.class,
                    () -> LeadSalesStageSnapshot.resolve(lead, value, dictionaries)).getCode());
        }
        assertEquals("intent_customer", lead.getSalesStage());
    }
}
