package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.biz.system.tenant.TenantCommonApi;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.definition.BpmCategoryApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmCategoryEnsureReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewConfigService;
import jakarta.annotation.Resource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.ContentReviewConstants.BPM_CATEGORY;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.MATERIAL_BPM_CATEGORY;

@Component
public class MaterialTenantInitializer {

    @Resource private MaterialTypeService materialTypeService;
    @Resource private ContentReviewConfigService contentReviewConfigService;
    @Resource private BpmCategoryApi bpmCategoryApi;
    @Resource private TenantCommonApi tenantApi;

    private static final List<BpmCategoryEnsureReqDTO> BPM_CATEGORIES = List.of(
            new BpmCategoryEnsureReqDTO("素材审批", MATERIAL_BPM_CATEGORY, "素材版本审批流程",
                    CommonStatusEnum.ENABLE.getStatus(), 610),
            new BpmCategoryEnsureReqDTO("生产内容批审", BPM_CATEGORY, "编导与终审逐条结论批审流程",
                    CommonStatusEnum.ENABLE.getStatus(), 620));

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        initializeTenant(event.getTenantId());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        tenantApi.getTenantIdList().forEach(this::initializeTenant);
    }

    private void initializeTenant(Long tenantId) {
        TenantUtils.execute(tenantId, () -> {
            materialTypeService.ensureDefaultTypes();
            contentReviewConfigService.ensureDefaultConfig();
            bpmCategoryApi.ensureCategories(BPM_CATEGORIES);
        });
    }
}
