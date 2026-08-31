package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadDuplicateMatcherTest {
    @InjectMocks private LeadDuplicateMatcher matcher;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadIntendedProductMapper productMapper;

    @Test
    void sameMobileOnActiveLeadIsStrongMatch() {
        LeadCreateReqVO request = request();
        PersonDO person = person(1L, "13800000000", null);
        LeadDO lead = lead(10L, 1L, "submitted");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of(person));
        when(leadMapper.selectByPersonIds(List.of(1L))).thenReturn(List.of(lead));
        when(leadMapper.selectByName("张三")).thenReturn(List.of());

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertNotNull(result.strongActiveMatch());
        assertTrue(result.strongActiveMatch().rules().contains(LeadDuplicateMatcher.SAME_MOBILE));
        assertTrue(result.strongDuplicate());
    }

    @Test
    void sameMobileOnInvalidLeadIsStrongMatch() {
        LeadCreateReqVO request = request();
        PersonDO person = person(1L, "13800000000", null);
        LeadDO lead = lead(10L, 1L, "invalid");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of(person));
        when(leadMapper.selectByPersonIds(List.of(1L))).thenReturn(List.of(lead));
        when(leadMapper.selectByName("张三")).thenReturn(List.of());

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertNotNull(result.strongActiveMatch());
        assertTrue(result.strongDuplicate());
    }

    @Test
    void crossContactIsWeakMatch() {
        LeadCreateReqVO request = request();
        PersonDO person = person(1L, null, "13800000000");
        LeadDO lead = lead(10L, 1L, "valid");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of(person));
        when(leadMapper.selectByPersonIds(List.of(1L))).thenReturn(List.of(lead));
        when(leadMapper.selectByName("张三")).thenReturn(List.of());

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertNull(result.strongActiveMatch());
        assertTrue(result.suspectedDuplicate());
        assertTrue(result.crossContactOnly());
        assertTrue(result.candidates().getFirst().rules().contains(LeadDuplicateMatcher.WEAK_MOBILE_TO_WECHAT));
    }

    @Test
    void placeholderNameSkipsOrdinaryWeakRules() {
        LeadCreateReqVO request = request();
        request.setName("微信用户");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of());

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertFalse(result.hasMatches());
    }

    @Test
    void otherRegionSkipsNameCityProductWeakRule() {
        LeadCreateReqVO request = request();
        request.setProvinceCode("OTHER");
        request.setCityCode("OTHER");
        LeadDO lead = lead(10L, 1L, "valid");
        lead.setSubmittedMobile("13900000000");
        lead.setProvinceCode("OTHER");
        lead.setCityCode("OTHER");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of());
        when(leadMapper.selectByName("张三")).thenReturn(List.of(lead));

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertFalse(result.hasMatches());
    }

    @Test
    void explicitNameCityProductIsWeakRule() {
        LeadCreateReqVO request = request();
        LeadDO lead = lead(10L, 1L, "valid");
        lead.setSubmittedMobile("13900000000");
        lead.setProvinceCode("110000");
        lead.setCityCode("110100");
        LeadIntendedProductDO product = new LeadIntendedProductDO();
        product.setSpuRef("SPU-1");
        product.setSpuUnknown(false);
        product.setSpuNameSnapshot("课程A");
        when(personMapper.selectDuplicateCandidates("13800000000", null)).thenReturn(List.of());
        when(leadMapper.selectByName("张三")).thenReturn(List.of(lead));
        when(productMapper.selectPrimaryByLeadId(10L)).thenReturn(product);
        when(personMapper.selectById(1L)).thenReturn(person(1L, "13900000000", null));

        LeadDuplicateMatcher.MatchResult result = matcher.match(request, null);

        assertTrue(result.suspectedDuplicate());
        assertTrue(result.candidates().getFirst().rules().contains(LeadDuplicateMatcher.NAME_REGION_PRIMARY_PRODUCT));
    }

    private static LeadCreateReqVO request() {
        LeadCreateReqVO request = new LeadCreateReqVO(); request.setName("张三"); request.setMobile("13800000000");
        request.setProvinceCode("110000"); request.setCityCode("110100");
        LeadProductReqVO product = new LeadProductReqVO(); product.setSpuRef("SPU-1"); product.setPrimary(true);
        request.setIntendedProducts(List.of(product)); return request;
    }
    private static PersonDO person(Long id, String mobile, String wechat) {
        PersonDO row = new PersonDO(); row.setId(id); row.setName("张三"); row.setMobile(mobile); row.setWechatId(wechat); return row;
    }
    private static LeadDO lead(Long id, Long personId, String status) {
        LeadDO row = new LeadDO(); row.setId(id); row.setPersonId(personId); row.setStatus(status); row.setAssignmentStatus("owned"); return row;
    }
}
