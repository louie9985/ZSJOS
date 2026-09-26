package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * 提现审批卡。
 *
 * <p>核心是<b>状态文案只有一个口径</b>。{@code approved} 的业务含义是「财务已通过、钱还没出去」，
 * 对财务要办的动作是登记打款，所以叫「待打款」。卡片层此前自己维护了一份映射并写成「已通过」，
 * 审批人看到这四个字会以为流程已经走完、没有下一步动作——而这恰恰是误会的来源。
 * 这里的断言刻意对着 {@link WithdrawalConstants#statusLabel} 而不是硬编码中文：
 * 以后改口径只改一处，测试仍应当通过；哪一层又私自写死一份，测试就红。
 */
@ExtendWith(MockitoExtension.class)
class WithdrawalContentProviderTest {

    private static final long WITHDRAWAL_ID = 12L;
    private static final String BUSINESS_KEY = "withdrawal:" + WITHDRAWAL_ID;
    /**
     * {@code detail()} / {@code brief()} 收到的是<b>已剥前缀</b>的 id 段——
     * 框架在 {@code BpmApprovalContentServiceImpl} 里先调 {@code parseBusinessId}。
     * 传完整 businessKey 进去会被 {@code Long.valueOf} 拒掉并静默返回 null，
     * 所以这两种形态要分开测。
     */
    private static final String BUSINESS_ID = String.valueOf(WITHDRAWAL_ID);
    private static final long VIEWER_ID = 30L;

    @Mock private WithdrawalService withdrawalService;
    @Mock private PermissionApi permissionApi;
    @Mock private AdminUserApi adminUserApi;

    private WithdrawalContentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WithdrawalContentProvider();
        ReflectionTestUtils.setField(provider, "withdrawalService", withdrawalService);
        ReflectionTestUtils.setField(provider, "permissionApi", permissionApi);
        ReflectionTestUtils.setField(provider, "adminUserApi", adminUserApi);
    }

    // ------------------------------------------------------------------ 状态文案

    @Test
    void approvedRendersAsPendingPayoutNotAsFinished() {
        stubRecord(WithdrawalConstants.STATUS_APPROVED);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_ID, VIEWER_ID);

        assertNotNull(card);
        assertEquals("待打款", card.getStatusText());
        assertEquals("待打款", fieldValue(card, "提现信息", "状态"));
        // 关键的负向断言：这个措辞会让人以为流程结束。
        assertEquals(false, "已通过".equals(card.getStatusText()), "approved 不得再显示为已通过");
    }

    @Test
    void everyKnownStatusUsesTheSharedLabel() {
        Map<String, String> expected = Map.of(
                WithdrawalConstants.STATUS_PENDING, "待审核",
                WithdrawalConstants.STATUS_APPROVED, "待打款",
                WithdrawalConstants.STATUS_REJECTED, "已驳回",
                WithdrawalConstants.STATUS_PAID, "已打款",
                WithdrawalConstants.STATUS_CANCELLED, "已取消");

        expected.forEach((status, label) -> {
            assertEquals(label, WithdrawalConstants.statusLabel(status), status);
            stubRecord(status);
            assertEquals(label, provider.detail(BUSINESS_ID, VIEWER_ID).getStatusText(), status);
        });
    }

    @Test
    void cancelledRendersAsCancelledNotAsRevoked() {
        // 卡片此前写「已撤销」，筛选目录写「已取消」。同一个状态两个词是同一类漂移，
        // 与目录的逐项一致性由 AdvancedFilterFieldCatalogTest 断言。
        stubRecord(WithdrawalConstants.STATUS_CANCELLED);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_ID, VIEWER_ID);

        assertEquals("已取消", card.getStatusText());
    }

    // ------------------------------------------------------------------ 其余展示

    @Test
    void briefCarriesStatusLabelAndAmount() {
        stubRecord(WithdrawalConstants.STATUS_APPROVED);

        BpmApprovalBriefVO brief = provider.brief(BUSINESS_ID, VIEWER_ID);

        assertNotNull(brief);
        assertEquals("待打款", fieldValue(brief, "状态"));
        assertEquals("1280.00", fieldValue(brief, "申请金额"));
    }

    @Test
    void financeViewerSeesPayoutEvidence() {
        WithdrawalRespVO record = stubRecord(WithdrawalConstants.STATUS_PAID);
        record.setPaidByUserId(77L);
        record.setPayoutRemark("线下转账已到账");
        lenient().when(permissionApi.hasAnyPermissions(VIEWER_ID, "zsjos:withdrawal:finance-query")).thenReturn(true);
        AdminUserRespDTO payer = new AdminUserRespDTO();
        payer.setNickname("财务小李");
        lenient().when(adminUserApi.getUser(77L)).thenReturn(payer);

        BpmApprovalDetailVO card = provider.detail(BUSINESS_ID, VIEWER_ID);

        assertEquals("财务小李", fieldValue(card, "打款信息", "打款人"));
        assertEquals("线下转账已到账", fieldValue(card, "打款信息", "打款备注"));
    }

    @Test
    void rejectionReasonShowsAsItsOwnGroup() {
        WithdrawalRespVO record = stubRecord(WithdrawalConstants.STATUS_REJECTED);
        record.setRejectionReason("银行卡号与开户名不符");

        BpmApprovalDetailVO card = provider.detail(BUSINESS_ID, VIEWER_ID);

        assertEquals("银行卡号与开户名不符", fieldValue(card, "驳回原因", "驳回原因"));
    }

    @Test
    void businessContentDisappearsInsteadOfThrowingWhenTheViewerCannotRead() {
        // 业务侧权限不足会抛异常，卡片应降级为"不展示业务内容"而不是把审批页整个打挂。
        lenient().when(withdrawalService.getDetail(anyLong(), anyLong(), anyBoolean()))
                .thenThrow(new IllegalStateException("无权查看该提现记录"));

        assertNull(provider.detail(BUSINESS_ID, VIEWER_ID));
        assertNull(provider.brief(BUSINESS_ID, VIEWER_ID));
    }

    @Test
    void nonNumericIdSegmentYieldsNull() {
        assertNull(provider.detail("abc", VIEWER_ID), "非数字 id 段不应抛异常");
        assertNull(provider.brief("", VIEWER_ID));
        assertNull(provider.detail(null, VIEWER_ID));
    }

    @Test
    void parseBusinessIdStripsThePrefixAndRejectsForeignKeys() {
        // 框架先剥前缀再交给 detail/brief；这里锁住剥离本身，避免静默展示到别的单据上。
        assertEquals(BUSINESS_ID, provider.parseBusinessId(BUSINESS_KEY));
        assertNull(provider.parseBusinessId("cashback:12"), "别的业务域前缀不得被接受");
        assertNull(provider.parseBusinessId("withdrawal"), "缺少 id 段");
        assertNull(provider.parseBusinessId("withdrawal:"));
    }

    // ------------------------------------------------------------------ 夹具

    private WithdrawalRespVO stubRecord(String status) {
        WithdrawalRespVO record = new WithdrawalRespVO();
        record.setId(WITHDRAWAL_ID);
        record.setWithdrawalNo("W-2026-0007");
        record.setStatus(status);
        record.setApplicationAmount(new BigDecimal("1280.00"));
        record.setAvailableBalanceSnapshot(new BigDecimal("3000.00"));
        record.setApprovedAmount(new BigDecimal("1280.00"));
        record.setAccountNameSnapshot("张三");
        record.setBankNameSnapshot("招商银行");
        record.setBranchNameSnapshot("北京分行");
        record.setMaskedCardNumber("****5678");
        record.setVerificationStatus(WithdrawalConstants.VERIFY_NORMAL);
        record.setSubmittedAt(LocalDateTime.of(2026, 9, 21, 10, 30));
        lenient().when(withdrawalService.getDetail(eq(WITHDRAWAL_ID), anyLong(), anyBoolean())).thenReturn(record);
        return record;
    }

    private static String fieldValue(BpmApprovalDetailVO card, String groupTitle, String label) {
        for (BpmApprovalDetailVO.Group group : card.getGroups()) {
            if (!groupTitle.equals(group.getTitle())) {
                continue;
            }
            for (BpmApprovalFieldVO field : group.getFields()) {
                if (label.equals(field.getLabel())) {
                    return field.getValue();
                }
            }
        }
        return null;
    }

    private static String fieldValue(BpmApprovalBriefVO brief, String label) {
        for (BpmApprovalFieldVO field : brief.getFields()) {
            if (label.equals(field.getLabel())) {
                return field.getValue();
            }
        }
        return null;
    }
}
