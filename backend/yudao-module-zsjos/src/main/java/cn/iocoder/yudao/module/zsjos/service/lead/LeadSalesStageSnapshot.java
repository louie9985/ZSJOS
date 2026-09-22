package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Sales progress is a dictionary snapshot, independent of qualification and deal state. */
public final class LeadSalesStageSnapshot {
    public static final String DICT_TYPE = "zsjos_lead_sales_stage";
    public static final String DEFAULT_VALUE = "pending_contact";
    private LeadSalesStageSnapshot() {}
    public record Selection(String value, String label) {}

    public static void initialize(LeadDO lead, DictDataApi dictionaries) {
        Selection selected = require(DEFAULT_VALUE, dictionaries, LEAD_SALES_STAGE_DEFAULT_INVALID);
        lead.setSalesStage(selected.value());
        lead.setSalesStageLabelSnapshot(selected.label());
    }

    public static Selection resolve(LeadDO lead, String requested, DictDataApi dictionaries) {
        // Omitted fields from older clients and unchanged choices retain their stored meaning.
        if (requested == null || Objects.equals(requested, lead.getSalesStage())) {
            return new Selection(lead.getSalesStage(), lead.getSalesStageLabelSnapshot());
        }
        return require(requested, dictionaries, LEAD_SALES_STAGE_INVALID);
    }

    private static Selection require(String value, DictDataApi dictionaries, ErrorCode error) {
        return dictionaries.getDictDataList(DICT_TYPE).stream()
                .filter(item -> Objects.equals(value, item.getValue())
                        && CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .findFirst().map(item -> new Selection(item.getValue(), item.getLabel()))
                .orElseThrow(() -> exception(error));
    }
}
