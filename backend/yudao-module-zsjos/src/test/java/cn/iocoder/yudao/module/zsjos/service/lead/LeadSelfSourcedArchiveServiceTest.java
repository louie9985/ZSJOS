package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionIdentityService.Identity.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class LeadSelfSourcedArchiveServiceTest {
    @InjectMocks LeadSelfSourcedArchiveService service;
    @Mock PersonMapper personMapper;
    @Mock LeadSubmissionIdentityService identityService;
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    private PersonDO person(long id) { return new PersonDO().setId(id).setName("测试档案").setMobile("13800000000").setWechatId("wx_test"); }
    private LeadCreateReqVO request() {
        var r = new LeadCreateReqVO(); r.setArchiveQueryConfirmed(true); r.setArchiveQueryWechatId("WX_TEST");
        r.setWechatId("wx_test"); r.setName("本次填写"); r.setProvinceCode("OTHER"); return r;
    }
    @Test void normalizesQueryAndReturnsFullIdentityWithoutLeadLookup() {
        when(personMapper.selectDuplicateCandidates(null,"wx_test")).thenReturn(List.of(person(11),person(12)));
        var candidates=service.query(null," WX_TEST ",7L,SALES);
        assertEquals(2,candidates.size()); assertEquals("13800000000",candidates.getFirst().getMobile());
        assertEquals("测试档案",candidates.getFirst().getName()); verify(identityService).requireSales(7L);
    }
    @Test void educationUsesItsOwnIdentityBoundary() {
        when(personMapper.selectDuplicateCandidates(null,"wx_test")).thenReturn(List.of());
        assertTrue(service.query(null,"wx_test",7L,EDUCATION).isEmpty());
        verify(identityService).requireEducationSubmitter(7L); verify(identityService,never()).requireSales(any());
    }
    @Test void deniedIdentityCannotReadCandidates() {
        doThrow(new ServiceException(LEAD_PERMISSION_DENIED)).when(identityService).requireSales(7L);
        assertThrows(ServiceException.class,()->service.query(null,"wx_test",7L,SALES)); verifyNoInteractions(personMapper);
    }
    @Test void emptyQueryCannotEnumerateArchives() {
        assertThrows(ServiceException.class,()->service.query(" "," ",7L,SALES)); verifyNoInteractions(personMapper);
    }
    @Test void missingQueryIsRejected() {
        assertEquals(LEAD_ARCHIVE_QUERY_REQUIRED.getCode(),assertThrows(ServiceException.class,()->service.requireSelection(new LeadCreateReqVO())).getCode());
        verifyNoInteractions(personMapper);
    }
    @Test void evenSingleCandidateRequiresSelectedIdAtServer() {
        when(personMapper.selectDuplicateCandidates(null,"wx_test")).thenReturn(List.of(person(11)));
        assertEquals(LEAD_ARCHIVE_SELECTION_REQUIRED.getCode(),assertThrows(ServiceException.class,()->service.requireSelection(request())).getCode());
        verify(personMapper,never()).selectDuplicateCandidatesForUpdate(any(),any(),any());
    }
    @Test void selectedArchiveCopiesOnlyIdentityAndNeverWritesPerson() {
        PersonDO selected=person(12);
        when(personMapper.selectDuplicateCandidates(null,"wx_test")).thenReturn(List.of(selected));
        var snapshot=service.query(null,"wx_test",7L,SALES).getFirst();
        when(personMapper.selectDuplicateCandidatesForUpdate(null,"wx_test",1L)).thenReturn(List.of(person(11),selected));
        var req=request(); req.setSelectedPersonId(12L);req.setArchiveSnapshotKey(snapshot.getSnapshotKey());
        assertSame(selected,service.requireSelection(req)); assertEquals("测试档案",req.getName());
        assertEquals("13800000000",req.getMobile()); assertEquals("OTHER",req.getProvinceCode());
        verify(personMapper,never()).updateById(any(PersonDO.class));
    }
    @Test void changedSnapshotOrForeignSelectionIsRejected() {
        when(personMapper.selectDuplicateCandidatesForUpdate(null,"wx_test",1L)).thenReturn(List.of(person(11)));
        var req=request();req.setSelectedPersonId(99L);
        assertEquals(LEAD_ARCHIVE_CHANGED.getCode(),assertThrows(ServiceException.class,()->service.requireSelection(req)).getCode());
        req.setSelectedPersonId(11L);req.setArchiveSnapshotKey("old");
        assertThrows(ServiceException.class,()->service.requireSelection(req));
    }
    @Test void noMatchAllowsCreationOnlyForUnchangedContacts() {
        when(personMapper.selectDuplicateCandidates(null,"wx_test")).thenReturn(List.of());
        var req=request(); assertNull(service.requireSelection(req));
        req.setWechatId("different"); assertThrows(ServiceException.class,()->service.requireSelection(req));
        verify(personMapper,never()).selectDuplicateCandidatesForUpdate(any(),any(),any());
    }
}
