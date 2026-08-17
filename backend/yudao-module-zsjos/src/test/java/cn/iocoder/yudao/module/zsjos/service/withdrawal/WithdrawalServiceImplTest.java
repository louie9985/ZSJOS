package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerWithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.APPROVE;
import static cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum.REJECT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {
    private final WithdrawalServiceImpl service = new WithdrawalServiceImpl();
    @Mock WithdrawalMapper withdrawalMapper; @Mock WithdrawalItemMapper itemMapper; @Mock PartnerBankCardMapper cardMapper;
    @Mock CashbackMapper cashbackMapper; @Mock PartnerMapper partnerMapper; @Mock BpmProcessInstanceApi processApi;
    @Mock BpmProcessTaskApi taskApi; @Mock AdminUserApi userApi; @Mock PermissionApi permissionApi;
    @Mock ConfigApi configApi; @Mock FileApi fileApi; @Mock BusinessAuditService auditService; @Mock WithdrawalNotifyPublisher publisher;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(9L);
        ReflectionTestUtils.setField(service,"withdrawalMapper",withdrawalMapper);ReflectionTestUtils.setField(service,"itemMapper",itemMapper);
        ReflectionTestUtils.setField(service,"cardMapper",cardMapper);ReflectionTestUtils.setField(service,"cashbackMapper",cashbackMapper);
        ReflectionTestUtils.setField(service,"partnerMapper",partnerMapper);ReflectionTestUtils.setField(service,"processInstanceApi",processApi);
        ReflectionTestUtils.setField(service,"processTaskApi",taskApi);ReflectionTestUtils.setField(service,"adminUserApi",userApi);
        ReflectionTestUtils.setField(service,"permissionApi",permissionApi);ReflectionTestUtils.setField(service,"configApi",configApi);
        ReflectionTestUtils.setField(service,"fileApi",fileApi);ReflectionTestUtils.setField(service,"auditService",auditService);
        ReflectionTestUtils.setField(service,"notifyPublisher",publisher);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}

    @Test void applyLocksSelectedCashbacksAndStartsSingleFinanceBpm() {
        when(partnerMapper.selectEnabledByUserId(7L)).thenReturn(new PartnerDO().setId(8L).setBoundSystemUserId(7L).setStatus("enabled"));
        CashbackDO one=cashback(1L,"12.00"), two=cashback(2L,"8.00");
        when(cashbackMapper.selectAvailableByBeneficiary(7L)).thenReturn(List.of(one,two));
        when(cashbackMapper.selectByIdForUpdate(1L,9L)).thenReturn(one); when(cashbackMapper.selectByIdForUpdate(2L,9L)).thenReturn(two);
        when(userApi.getUserListByStatus(0)).thenReturn(List.of(new AdminUserRespDTO().setId(30L).setStatus(0)));
        when(permissionApi.hasAnyPermissions(30L,"zsjos:withdrawal:review")).thenReturn(true);
        when(cashbackMapper.transitionStatus(anyLong(),eq(0),eq("available"),eq("withdrawing"))).thenReturn(1);
        doAnswer(inv->{invocationWithdrawal(inv).setId(50L);return 1;}).when(withdrawalMapper).insert(any(WithdrawalDO.class));
        when(processApi.createProcessInstance(eq(7L),any())).thenReturn("p1");
        WithdrawalApplyReqVO request=new WithdrawalApplyReqVO();request.setCashbackIds(List.of(2L,1L));request.setAccountName("张三");
        request.setCardNumber("6222 0000 0000 1234");request.setBankName("中世健银行");
        assertEquals(50L,service.apply(7L,request));
        verify(cashbackMapper).selectByIdForUpdate(1L,9L);verify(cashbackMapper).selectByIdForUpdate(2L,9L);
        verify(itemMapper,times(2)).insert(any(WithdrawalItemDO.class));verify(processApi).createProcessInstance(eq(7L),argThat(p->p.getStartUserSelectAssignees().get("financeReview").equals(List.of(30L))));
    }

    @Test void rejectProcessRestoresCashbacksAndDeactivatesRelation() {
        WithdrawalDO row=withdrawal(50L,"pending_review");
        when(withdrawalMapper.selectByProcessInstanceId("p1")).thenReturn(row);when(withdrawalMapper.selectByIdForUpdate(50L,9L)).thenReturn(row);
        WithdrawalItemDO item=new WithdrawalItemDO().setCashbackId(1L).setActiveFlag(true);
        when(itemMapper.selectByWithdrawalId(50L)).thenReturn(List.of(item));CashbackDO cashback=cashback(1L,"20").setStatus("withdrawing");
        when(cashbackMapper.selectByIdForUpdate(1L,9L)).thenReturn(cashback);when(cashbackMapper.transitionStatus(1L,0,"withdrawing","available")).thenReturn(1);
        when(userApi.getUserListByStatus(0)).thenReturn(List.of());
        service.handleProcessResult("p1",REJECT.getStatus(),"资料有误");
        assertEquals("rejected",row.getStatus());verify(itemMapper).deactivate(50L);
    }

    @Test void approvedProcessKeepsCashbackWithdrawing() {
        WithdrawalDO row=withdrawal(50L,"pending_review");when(withdrawalMapper.selectByProcessInstanceId("p1")).thenReturn(row);
        when(withdrawalMapper.selectByIdForUpdate(50L,9L)).thenReturn(row);when(taskApi.getProcessNodeStatuses(eq("p1"),anySet())).thenReturn(List.of());
        when(userApi.getUserListByStatus(0)).thenReturn(List.of());service.handleProcessResult("p1",APPROVE.getStatus(),null);
        assertEquals("approved",row.getStatus());assertEquals(new BigDecimal("20.00"),row.getApprovedAmount());verifyNoInteractions(cashbackMapper);
    }

    @Test void payoutRequiresOwnedProofAndWithdrawsCashback() {
        WithdrawalDO row=withdrawal(50L,"approved");when(withdrawalMapper.selectByIdForUpdate(50L,9L)).thenReturn(row);
        when(withdrawalMapper.selectByTransactionNo("TX1")).thenReturn(null);
        when(fileApi.getFileInfo(90L)).thenReturn(new FileInfoRespDTO(1L,1L,"proof.pdf","zsjos/withdrawal-proof/x","u","application/pdf",100L,"30"));
        when(itemMapper.selectByWithdrawalId(50L)).thenReturn(List.of(new WithdrawalItemDO().setCashbackId(1L).setActiveFlag(true)));
        CashbackDO cashback=cashback(1L,"20").setStatus("withdrawing");when(cashbackMapper.selectByIdForUpdate(1L,9L)).thenReturn(cashback);
        when(cashbackMapper.transitionStatus(1L,0,"withdrawing","withdrawn")).thenReturn(1);when(userApi.getUserListByStatus(0)).thenReturn(List.of());
        WithdrawalPayoutReqVO req=new WithdrawalPayoutReqVO();req.setBankTransactionNo("TX1");req.setProofFileId(90L);
        service.recordPayout(50L,30L,req);assertEquals("paid",row.getStatus());assertEquals(30L,row.getPaidByUserId());verify(auditService).record(any(),any(),any(),any(),any(),any());
    }

    @Test void ordinaryDetailRedactsFinanceFieldsAndDoesNotCreateProofUrl() {
        WithdrawalDO row = withdrawal(50L, "paid");
        row.setBankTransactionNo("TX1"); row.setProofFileId(90L); row.setPayoutRemark("已打款");
        row.setPaidByUserId(30L); row.setPaidAt(LocalDateTime.of(2026, 8, 15, 10, 0));
        when(withdrawalMapper.selectById(50L)).thenReturn(row);
        when(itemMapper.selectByWithdrawalId(50L)).thenReturn(List.of());

        WithdrawalRespVO response = service.getDetail(50L, 20L, false);

        assertNull(response.getBankTransactionNo()); assertNull(response.getProofFileId());
        assertNull(response.getProofUrl()); assertNull(response.getPayoutRemark());
        assertNull(response.getPaidByUserId()); assertNull(response.getPaidAt());
        verify(fileApi, never()).presignGetUrl(anyLong(), anyInt());
    }

    @Test void financeDetailReturnsFinanceFieldsAndAuditsAccess() {
        WithdrawalDO row = withdrawal(50L, "paid");
        row.setCardNumberSnapshot("622200001234"); row.setBankTransactionNo("TX1"); row.setProofFileId(90L);
        when(withdrawalMapper.selectById(50L)).thenReturn(row);
        when(permissionApi.hasAnyPermissions(30L, "zsjos:withdrawal:finance-query")).thenReturn(true);
        when(itemMapper.selectByWithdrawalId(50L)).thenReturn(List.of());
        when(fileApi.presignGetUrl(90L, 600)).thenReturn("https://signed.test/proof");

        WithdrawalRespVO response = service.getDetail(50L, 30L, true);

        assertEquals("TX1", response.getBankTransactionNo());
        assertEquals("https://signed.test/proof", response.getProofUrl());
        assertEquals("622200001234", response.getCardNumber());
        verify(auditService).record(any(), any(), any(), eq("50"), eq("finance"), any());
    }

    @Test void partnerDetailUsesDedicatedRedactedContract() {
        WithdrawalDO row = withdrawal(50L, "paid").setPartnerId(8L).setCardNumberSnapshot("622200001234")
                .setBankNameSnapshot("中世健银行").setBankTransactionNo("TX1").setProofFileId(90L)
                .setPaidByUserId(30L);
        when(withdrawalMapper.selectById(50L)).thenReturn(row);

        PartnerWithdrawalRespVO response = service.getPartnerDetail(50L, 8L);

        assertEquals(50L, response.getId());
        assertEquals("中世健银行", response.getBankNameSnapshot());
        assertNotEquals("622200001234", response.getMaskedCardNumber());
        assertFalse(java.util.Arrays.stream(PartnerWithdrawalRespVO.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("processInstanceId") || field.getName().equals("paidByUserId")
                        || field.getName().equals("proofFileId") || field.getName().equals("bankTransactionNo")));
    }

    private WithdrawalDO invocationWithdrawal(org.mockito.invocation.InvocationOnMock inv){return inv.getArgument(0);}
    private CashbackDO cashback(long id,String amount){return new CashbackDO().setId(id).setPartnerId(8L).setBeneficiaryUserId(7L).setStatus("available").setAmount(new BigDecimal(amount)).setVersion(0);}
    private WithdrawalDO withdrawal(long id,String status){return new WithdrawalDO().setId(id).setApplicantUserId(7L).setStatus(status).setApplicationAmount(new BigDecimal("20.00")).setProcessInstanceId("p1").setVersion(0);}
}
