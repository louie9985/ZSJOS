package cn.iocoder.yudao.module.bpm.api.definition;

import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmCategoryEnsureReqDTO;

import java.util.List;

/** Public BPM boundary for idempotently provisioning system-owned process categories. */
public interface BpmCategoryApi {

    void ensureCategories(List<BpmCategoryEnsureReqDTO> categories);
}
