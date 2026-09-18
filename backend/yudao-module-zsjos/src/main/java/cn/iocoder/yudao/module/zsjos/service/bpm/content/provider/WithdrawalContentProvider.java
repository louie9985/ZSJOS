package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提现审批的业务内容。
 *
 * <p>businessKey 形如 {@code withdrawal:12}。复用 {@code WithdrawalService.getDetail} 而不是
 * 直接查表：业务侧已经在那儿处理了银行卡脱敏与打款信息裁剪，审批中心不该另起一套。
 */
@Component
public class WithdrawalContentProvider implements BpmApprovalContentProvider {

    public static final String PREFIX = "withdrawal:";
    /** 财务视角才能看到打款凭证、流水号等结清信息。 */
    private static final String PERMISSION_FINANCE_QUERY = "zsjos:withdrawal:finance-query";

    @Resource
    private WithdrawalService withdrawalService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "withdrawal";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, businessKey));
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        Long id = parseId(businessId);
        if (id == null) {
            return null;
        }
        WithdrawalRespVO detail = load(id, viewerId);
        if (detail == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(detail.getWithdrawalNo());
        brief.setSubtitle("申请金额 " + BpmApprovalFormat.amount(detail.getApplicationAmount()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("申请金额", BpmApprovalFormat.amount(detail.getApplicationAmount())),
                BpmApprovalFieldVO.of("可用余额", BpmApprovalFormat.amount(detail.getAvailableBalanceSnapshot())),
                BpmApprovalFieldVO.of("收款人", detail.getAccountNameSnapshot()),
                BpmApprovalFieldVO.of("状态", statusText(detail.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        Long id = parseId(businessId);
        if (id == null) {
            return null;
        }
        WithdrawalRespVO item = load(id, viewerId);
        if (item == null) {
            return null;
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(item.getWithdrawalNo());
        card.setStatusText(statusText(item.getStatus()));

        card.getGroups().add(group("提现信息", List.of(
                BpmApprovalFieldVO.of("提现单号", item.getWithdrawalNo()),
                BpmApprovalFieldVO.of("状态", statusText(item.getStatus())),
                BpmApprovalFieldVO.of("申请金额", BpmApprovalFormat.amount(item.getApplicationAmount())),
                BpmApprovalFieldVO.of("可用余额", BpmApprovalFormat.amount(item.getAvailableBalanceSnapshot())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(item.getSubmittedAt())),
                BpmApprovalFieldVO.of("核验结果", verificationText(item.getVerificationStatus())))));

        card.getGroups().add(group("收款信息", List.of(
                BpmApprovalFieldVO.of("收款人", item.getAccountNameSnapshot()),
                BpmApprovalFieldVO.of("银行卡", item.getMaskedCardNumber()),
                BpmApprovalFieldVO.of("开户行", item.getBankNameSnapshot()),
                BpmApprovalFieldVO.of("支行", item.getBranchNameSnapshot()))));

        if (item.getRejectionReason() != null) {
            card.getGroups().add(wideGroup("驳回原因", item.getRejectionReason()));
        }

        // 财务结清信息只有财务视角可见；业务侧 toResponse 已经按 fullCard 裁掉了这部分。
        if (viewerId != null && permissionApi.hasAnyPermissions(viewerId, PERMISSION_FINANCE_QUERY)) {
            card.getGroups().add(group("打款信息", List.of(
                    BpmApprovalFieldVO.of("通过金额", BpmApprovalFormat.amount(item.getApprovedAmount())),
                    BpmApprovalFieldVO.of("银行流水号", item.getBankTransactionNo()),
                    BpmApprovalFieldVO.of("打款人", userName(item.getPaidByUserId())),
                    BpmApprovalFieldVO.of("打款时间", BpmApprovalFormat.dateTime(item.getPaidAt())),
                    BpmApprovalFieldVO.wide("打款备注", item.getPayoutRemark()))));
        }
        return card;
    }

    /**
     * 复用业务侧的详情查询。财务视角才取全量（含打款信息）；
     * 权限不足时业务侧抛异常，这里降级为"不展示业务内容"。
     */
    private WithdrawalRespVO load(Long id, Long viewerId) {
        boolean finance = viewerId != null && permissionApi.hasAnyPermissions(viewerId, PERMISSION_FINANCE_QUERY);
        try {
            return withdrawalService.getDetail(id, viewerId, finance);
        } catch (Exception ex) {
            return null;
        }
    }

    private Long parseId(String businessId) {
        try {
            return Long.valueOf(businessId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case WithdrawalConstants.STATUS_PENDING -> "待审核";
            case WithdrawalConstants.STATUS_APPROVED -> "已通过";
            case WithdrawalConstants.STATUS_REJECTED -> "已驳回";
            case WithdrawalConstants.STATUS_PAID -> "已打款";
            case WithdrawalConstants.STATUS_CANCELLED -> "已撤销";
            default -> status;
        };
    }

    private static String verificationText(String verification) {
        if (verification == null) {
            return null;
        }
        return switch (verification) {
            case WithdrawalConstants.VERIFY_NORMAL -> "正常";
            case WithdrawalConstants.VERIFY_AMOUNT -> "金额异常";
            case WithdrawalConstants.VERIFY_DUPLICATE -> "重复申请";
            case WithdrawalConstants.VERIFY_BALANCE -> "余额异常";
            default -> verification;
        };
    }

    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new java.util.ArrayList<>(fields));
        return group;
    }

    private static BpmApprovalDetailVO.Group wideGroup(String title, String value) {
        BpmApprovalDetailVO.Group group = group(title, List.of(BpmApprovalFieldVO.of(title, value)));
        group.setSpan(true);
        return group;
    }
}
