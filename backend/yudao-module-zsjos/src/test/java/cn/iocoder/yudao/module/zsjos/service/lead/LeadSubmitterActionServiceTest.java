package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadUrgeMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadSubmitterActionServiceTest {
    @InjectMocks private LeadSubmitterActionService service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadIntendedProductMapper productMapper;
    @Mock private AreaApi areaApi;
    @Mock private ZsjosProductSkuService productSkuService;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private LeadUrgeMapper urgeMapper;
    @Mock private LeadNotifyEventPublisher notifyPublisher;
    @Mock private LeadSubmissionIdentityService identityService;
    @Mock private LeadCategorySnapshotService categorySnapshotService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;

    @Test
    void internalSubmitterSupplementCompletesSubmitterAssistTask() {
        stubSupplement(new LeadDO().setId(1L).setStatus("submitted").setProviderOwnerType("system_user")
                .setProviderOwnerId(30L).setSourceUserId(30L));

        withTenant(() -> service.supplement(1L, 30L, request()));

        verify(lifecycleTaskService).completeSubmitterAssistTasks(eq(1L), eq(30L), any(LocalDateTime.class));
    }

    @Test
    void partnerSupplementDoesNotCompleteAdminBusinessTask() {
        stubSupplement(new LeadDO().setId(1L).setStatus("submitted").setProviderOwnerType("partner")
                .setProviderOwnerId(70L).setPartnerId(70L));

        withTenant(() -> service.supplementForPartner(1L, 70L, request()));

        verify(lifecycleTaskService, never()).completeSubmitterAssistTasks(anyLong(), anyLong(), any());
    }

    private void stubSupplement(LeadDO lead) {
        when(eventMapper.selectByIdempotencyKey("supplement-1")).thenReturn(null);
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        AreaRespDTO province = area(110000, Area.ID_CHINA, 2, "北京市");
        AreaRespDTO city = area(110100, 110000, 3, "北京市");
        when(areaApi.getArea(110000)).thenReturn(province);
        when(areaApi.getArea(110100)).thenReturn(city);
        when(categorySnapshotService.requireEnabled("a"))
                .thenReturn(new LeadCategorySnapshotService.Selection("a", "A 类"));
        when(productSkuService.validateLeadProduct(eq("spu-1"), eq(false), eq("sku-1"), eq(false)))
                .thenReturn(new LeadProductSnapshot("spu-1", "课程", null, null, List.of(), null, null,
                        null, null, "sku-1", "规格", "{}", BigDecimal.TEN, false, false));
        doAnswer(invocation -> {
            invocation.<BusinessEventDO>getArgument(0).setOccurredAt(LocalDateTime.of(2026, 8, 29, 10, 0));
            return 1;
        }).when(eventMapper).insert(any(BusinessEventDO.class));
    }

    private LeadSubmitterSupplementReqVO request() {
        LeadProductReqVO product = new LeadProductReqVO();
        product.setSpuRef("spu-1"); product.setSkuRef("sku-1"); product.setPrimary(true);
        LeadSubmitterSupplementReqVO request = new LeadSubmitterSupplementReqVO();
        request.setProvinceCode("110000"); request.setCityCode("110100"); request.setLeadCategory("a");
        request.setRemark("已补充联系方式"); request.setIntendedProducts(List.of(product));
        request.setIdempotencyKey("supplement-1");
        return request;
    }

    private AreaRespDTO area(Integer id, Integer parentId, Integer type, String name) {
        AreaRespDTO area = new AreaRespDTO();
        area.setId(id); area.setParentId(parentId); area.setType(type); area.setName(name);
        area.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return area;
    }

    private void withTenant(Runnable runnable) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            runnable.run();
        }
    }
}
