package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.DICT_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CATEGORY_INVALID;

@Service
public class LeadCategorySnapshotService {

    @Resource
    private DictDataApi dictDataApi;

    public Selection requireEnabled(String rawValue) {
        String value = StrUtil.trimToNull(rawValue);
        if (value == null) {
            return new Selection(null, null);
        }
        DictDataRespDTO data = dictDataApi.getDictDataList(DICT_CATEGORY).stream()
                .filter(item -> Objects.equals(item.getValue(), value))
                .filter(item -> CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))
                .findFirst().orElseThrow(() -> exception(LEAD_CATEGORY_INVALID));
        return new Selection(data.getValue(), data.getLabel());
    }

    public record Selection(String value, String labelSnapshot) {
    }
}
