package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductCatalogPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CONTACT_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_MOBILE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_REGION_INVALID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadSubmissionServiceImplTest {
    @InjectMocks private LeadSubmissionServiceImpl service;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadActivationMapper activationMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;
    @Mock private LeadAttachmentMapper attachmentMapper;
    @Mock private AreaApi areaApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private LeadProductCatalogPort productCatalogPort;
    @Mock private LeadDispatchService dispatchService;
    @Mock private LeadAttachmentService attachmentService;

    @Test
    void createRejectsMissingMobileAndWechat() {
        LeadCreateReqVO req = baseRequest();

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(req, 1L));

        assertEquals(LEAD_CONTACT_REQUIRED.getCode(), error.getCode());
    }

    @Test
    void createRejectsMalformedMobileBeforeRemoteValidation() {
        LeadCreateReqVO req = baseRequest();
        req.setMobile("12345");

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(req, 1L));

        assertEquals(LEAD_MOBILE_INVALID.getCode(), error.getCode());
    }

    @Test
    void validateRegionAcceptsEnabledProvinceAndCity() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getArea(110100)).thenReturn(area(110100, 3, 110000, 0));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "110100"));
    }

    @Test
    void validateRegionRejectsDisabledOrCrossProvinceCity() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getArea(110100)).thenReturn(area(110100, 3, 120000, 0));
        ServiceException crossProvince = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "110100"));
        assertEquals(LEAD_REGION_INVALID.getCode(), crossProvince.getCode());

        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 1));
        ServiceException disabled = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "OTHER"));
        assertEquals(LEAD_REGION_INVALID.getCode(), disabled.getCode());
    }

    @Test
    void validateRegionAcceptsConfiguredProvinceOther() {
        AreaRespDTO province = area(110000, 2, 1, 0);
        AreaRespDTO otherCity = area(900000011, 3, 110000, 0);
        otherCity.setName("其他地区");
        otherCity.setSelectionCode("OTHER");
        when(areaApi.getArea(110000)).thenReturn(province);
        when(areaApi.getAreaByParentIdAndSelectionCode(110000, "OTHER")).thenReturn(otherCity);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "110000", "OTHER");

        assertEquals("其他地区", snapshot.cityName());
    }

    @Test
    void validateRegionAcceptsConfiguredDirectProvinceLeaf() {
        AreaRespDTO hongKong = area(810000, 2, 1, 0);
        hongKong.setLeafSelectable(true);
        when(areaApi.getArea(810000)).thenReturn(hongKong);
        when(areaApi.getAreaByParentIdAndSelectionCode(810000, "OTHER")).thenReturn(null);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "810000", "OTHER");

        assertEquals("810000", snapshot.provinceCode());
        assertEquals("OTHER", snapshot.cityCode());
        assertNull(snapshot.cityName());
    }

    @Test
    void validateRegionRejectsDisabledConfiguredOther() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getAreaByParentIdAndSelectionCode(110000, "OTHER"))
                .thenReturn(area(900000011, 3, 110000, 1));

        ServiceException error = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "OTHER"));

        assertEquals(LEAD_REGION_INVALID.getCode(), error.getCode());
    }

    @Test
    void validateRegionAcceptsConfiguredOtherProvinceAndCity() {
        AreaRespDTO otherProvince = area(990000000, 2, 1, 0);
        otherProvince.setName("其他省份");
        otherProvince.setSelectionCode("OTHER");
        AreaRespDTO otherCity = area(990000001, 3, 990000000, 0);
        otherCity.setName("其他城市");
        otherCity.setSelectionCode("OTHER");
        when(areaApi.getAreaByParentIdAndSelectionCode(1, "OTHER")).thenReturn(otherProvince);
        when(areaApi.getAreaByParentIdAndSelectionCode(990000000, "OTHER")).thenReturn(otherCity);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "OTHER", "OTHER");

        assertEquals("其他省份", snapshot.provinceName());
        assertEquals("其他城市", snapshot.cityName());
    }

    private static AreaRespDTO area(int id, int type, int parentId, int status) {
        AreaRespDTO area = new AreaRespDTO();
        area.setId(id);
        area.setName(String.valueOf(id));
        area.setType(type);
        area.setParentId(parentId);
        area.setSelectionCode(String.valueOf(id));
        area.setLeafSelectable(false);
        area.setStatus(status == 0 ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        return area;
    }

    private static LeadCreateReqVO baseRequest() {
        LeadCreateReqVO req = new LeadCreateReqVO();
        req.setName("测试客户");
        req.setProvinceCode("OTHER");
        req.setCityCode("OTHER");
        req.setSourceChannel("test");
        req.setLeadCategory("test");
        req.setDispatchMode("auto");
        req.setIdempotencyKey("test-idempotency-key");
        return req;
    }
}
