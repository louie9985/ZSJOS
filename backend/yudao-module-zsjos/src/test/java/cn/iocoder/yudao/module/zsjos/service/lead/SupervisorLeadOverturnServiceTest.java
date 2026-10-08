package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.LeadOverturnValidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.SupervisorLeadOverturnPolicy.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class SupervisorLeadOverturnServiceTest {
    @InjectMocks SupervisorLeadOverturnService service;
    @Mock LeadMapper leadMapper;
    @Mock LeadAppealMapper appealMapper;
    @Mock OpportunityMapper opportunityMapper;
    @Mock LeadIntendedProductMapper intendedProductMapper;
    @Mock BusinessEventMapper eventMapper;
    @Mock LeadObjectPermissionService permissionService;
    @Mock SecurityFrameworkService securityFrameworkService;
    @Mock LeadAttachmentService attachmentService;
    @Mock CashbackService cashbackService;
    @Mock LeadNotifyEventPublisher notifyEventPublisher;
    LeadDO lead;
    @BeforeAll static void mapping() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        for (Class<?> type : List.of(LeadDO.class, OpportunityDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "overturn-test"), type);
        }
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9L);
        lead = new LeadDO(); lead.setId(1L); lead.setTenantId(9L); lead.setPersonId(5L);
        lead.setOwnerUserId(20L); lead.setStatus("invalid"); lead.setAssignmentStatus("owned");
        lead.setQualifiedAt(LocalDateTime.of(2026,10,8,10,0)); lead.setVersion(1);
        lead.setInvalidReason("unreachable"); lead.setInvalidReasonLabelSnapshot("原无效分类");
        lead.setInvalidDescription("原无效说明"); lead.setInvalidEvidenceRefs("[]");
        lead.setLeadCategory("A"); lead.setLeadCategoryLabelSnapshot("历史分类");
        lenient().when(leadMapper.selectByIdForUpdate(1L,9L)).thenReturn(lead);
        lenient().when(permissionService.getManagedUserIds(30L)).thenReturn(Set.of(20L));
        lenient().when(securityFrameworkService.hasPermission(PERMISSION)).thenReturn(true);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    LeadOverturnValidReqVO request() {
        var r = new LeadOverturnValidReqVO(); r.setReason("  已补充有效证据  ");
        r.setIdempotencyKey("request-1"); r.setQualificationToken(token(lead,null)); return r;
    }
    @Test void restoresWithoutAppealAndPreservesOwnershipAndHistory() {
        service.overturn(1L,30L,request());
        assertEquals("valid",lead.getStatus()); assertEquals(20L,lead.getOwnerUserId());
        assertEquals("历史分类",lead.getLeadCategoryLabelSnapshot()); assertEquals("已补充有效证据",lead.getValidDescription());
        assertNull(lead.getInvalidReason()); assertNull(lead.getInvalidEvidenceRefs());
        verify(opportunityMapper).insert(argThat((OpportunityDO o) -> "open".equals(o.getStatus()) && o.getOwnerUserId()==20L));
        verify(leadMapper).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(cashbackService).ensureValidCashback(1L);
        verify(eventMapper).insert(argThat((BusinessEventDO e) -> EVENT.equals(e.getEventType())
                && "invalid".equals(e.getFromStatus()) && e.getRelatedObjectRefs().contains("原无效说明")));
        verify(notifyEventPublisher).publish(eq(SCENE),eq(1L),anyString(),eq(30L),any(),
                argThat(context -> Long.valueOf(20L).equals(context.get("ownerUserId"))
                        && "已补充有效证据".equals(context.get("overturn.reason"))));
        verify(appealMapper,never()).insert(any(LeadAppealDO.class));
    }
    @Test void firstRoundUpheldRestoresLostOpportunity() {
        var appeal = appeal(1,"upheld"); when(appealMapper.selectListByLeadId(1L)).thenReturn(List.of(appeal));
        var o = new OpportunityDO(); o.setId(7L); o.setType("initial_conversion"); o.setStatus("lost"); o.setLostReason("旧原因");
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(o);
        service.overturn(1L,30L,request()); assertEquals("open",o.getStatus()); assertNull(o.getLostReason());
        verify(opportunityMapper,never()).insert(any(OpportunityDO.class));
    }
    @ParameterizedTest @ValueSource(strings={"submitted","sales_manager_reviewing","quality_reviewing","chairman_reviewing","overturned","withdrawn"})
    void rejectsOtherAppealOutcomes(String status) {
        when(appealMapper.selectListByLeadId(1L)).thenReturn(List.of(appeal(1,status)));
        assertEquals(APPEAL_BLOCKED.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,request())).getCode());
        verifyNoInteractions(cashbackService,notifyEventPublisher);
    }
    @ParameterizedTest @ValueSource(ints={2,3}) void rejectsHigherRoundEvenIfUpheld(int round) {
        when(appealMapper.selectListByLeadId(1L)).thenReturn(List.of(appeal(round,"upheld")));
        assertNull(service.action(lead,30L));
        assertThrows(ServiceException.class,()->service.overturn(1L,30L,request()));
    }
    @ParameterizedTest @ValueSource(strings={"submitted","valid","won","closed","suspended"})
    void rejectsOtherLeadStates(String status) {
        lead.setStatus(status); assertThrows(ServiceException.class,()->service.overturn(1L,30L,request()));
    }
    @Test void rejectsUnownedAndWrongDepartmentDespiteReadAll() {
        when(permissionService.getManagedUserIds(30L)).thenReturn(Set.of());
        assertNull(service.action(lead,30L));
        assertEquals(LEAD_PERMISSION_DENIED.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,request())).getCode());
        lead.setOwnerUserId(null); assertThrows(ServiceException.class,()->service.overturn(1L,30L,request()));
        verify(leadMapper,never()).updateById(any(LeadDO.class));
    }
    @Test void featurePermissionIsRequired() {
        when(securityFrameworkService.hasPermission(PERMISSION)).thenReturn(false);
        assertNull(service.action(lead,30L)); assertThrows(ServiceException.class,()->service.overturn(1L,30L,request()));
        verifyNoInteractions(eventMapper);
    }
    @Test void tenantScopedLockCannotFindForeignLead() {
        TenantContextHolder.setTenantId(10L);
        assertEquals(LEAD_NOT_EXISTS.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,request())).getCode());
        verify(leadMapper).selectByIdForUpdate(1L,10L); verifyNoInteractions(eventMapper);
    }
    @Test void rejectsOldOwnerOrNewInvalidDecision() {
        var r=request(); lead.setOwnerUserId(21L); when(permissionService.getManagedUserIds(30L)).thenReturn(Set.of(20L,21L));
        assertEquals(STALE.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,r)).getCode());
        lead.setOwnerUserId(20L); var event=new BusinessEventDO();event.setId(100L);event.setEventType("lead_qualified_invalid");
        when(eventMapper.selectByLeadId(1L)).thenReturn(List.of(event));
        assertEquals(STALE.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,r)).getCode());
    }
    @Test void rejectsWonOpportunity() {
        var o=new OpportunityDO();o.setStatus("won");o.setType("initial_conversion");when(opportunityMapper.selectByLeadId(1L)).thenReturn(o);
        assertEquals(OPPORTUNITY_INVALID.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,request())).getCode());
    }
    @Test void retryDoesNotRepeatSideEffectsAndChangedPayloadConflicts() {
        var r=request(); service.overturn(1L,30L,r);
        var capture=ArgumentCaptor.forClass(BusinessEventDO.class);verify(eventMapper).insert(capture.capture());
        when(eventMapper.selectByIdempotencyKeyForUpdate(anyString())).thenReturn(capture.getValue());
        service.overturn(1L,30L,r); verify(cashbackService,times(1)).ensureValidCashback(1L);
        r.setReason("其他理由");
        assertEquals(SUBORDINATE_COMMAND_IDEMPOTENCY_CONFLICT.getCode(),assertThrows(ServiceException.class,()->service.overturn(1L,30L,r)).getCode());
    }
    private LeadAppealDO appeal(int round,String status) {
        var a=new LeadAppealDO();a.setRoundNo(round);a.setStatus(status);a.setReviewStage(round==1?"sales_manager":round==2?"quality":"chairman");return a;
    }
}
