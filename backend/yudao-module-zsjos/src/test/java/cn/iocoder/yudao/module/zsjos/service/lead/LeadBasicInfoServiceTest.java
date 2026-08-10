package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_BASIC_INFO_CONTACT_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadBasicInfoServiceTest {
    @InjectMocks private LeadBasicInfoService service;
    @Mock private LeadMapper leadMapper;
    @Mock private PersonMapper personMapper;
    @Mock private LeadIntendedProductMapper productMapper;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private AreaApi areaApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private ZsjosProductSkuService productSkuService;
    @Mock private BusinessEventMapper eventMapper;

    @Test
    void updateRejectsNonOwnerBeforeMutation() {
        LeadDO lead = editableLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);

        ServiceException error = withTenantError(() -> service.update(1L, 99L, request()));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
    }

    @Test
    void updateRejectsContactOwnedByAnotherPerson() {
        LeadDO lead = editableLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        PersonDO conflict = new PersonDO(); conflict.setId(200L);
        when(personMapper.selectByMobile("13900000000")).thenReturn(conflict);

        ServiceException error = withTenantError(() -> service.update(1L, 20L, request()));

        assertEquals(LEAD_BASIC_INFO_CONTACT_CONFLICT.getCode(), error.getCode());
    }

    private LeadDO editableLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setPersonId(100L); lead.setOwnerUserId(20L);
        lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        return lead;
    }

    private LeadBasicInfoUpdateReqVO request() {
        LeadBasicInfoUpdateReqVO request = new LeadBasicInfoUpdateReqVO();
        request.setName("测试客户"); request.setMobile("13900000000");
        request.setProvinceCode("110000"); request.setCityCode("110100");
        request.setReason("客户要求更正");
        return request;
    }

    private ServiceException withTenantError(Runnable action) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            return assertThrows(ServiceException.class, action::run);
        }
    }
}
