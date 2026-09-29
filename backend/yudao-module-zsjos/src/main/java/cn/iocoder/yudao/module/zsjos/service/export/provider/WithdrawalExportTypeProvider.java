package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalReviewService;
import cn.iocoder.yudao.module.zsjos.service.cashback.FinanceTraceService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.EXPORT_PERMISSION_DENIED;

@Component
public class WithdrawalExportTypeProvider extends AbstractPagedExportTypeProvider<WithdrawalPageReqVO, WithdrawalRespVO> {

    @Resource
    private WithdrawalService withdrawalService;
    @Resource private PermissionApi permissionApi;
    @Resource private FinanceTraceService traceService;
    @Resource private WithdrawalReviewService reviewService;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override public String getType() { return "withdrawal"; }
    @Override public String getCreatePermission() { return "zsjos:export:withdrawal"; }
    @Override public void checkCreator(Long userId) {
        // Full account exports require the same configured scope as management reads.
        if (!permissionApi.hasAnyPermissions(userId, "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query")) {
            throw exception(EXPORT_PERMISSION_DENIED);
        }
    }
    @Override protected Class<WithdrawalPageReqVO> requestType() { return WithdrawalPageReqVO.class; }
    @Override protected PageResult<WithdrawalRespVO> getPage(WithdrawalPageReqVO request, Long creatorUserId) {
        checkCreator(creatorUserId);
        PageResult<WithdrawalRespVO> page = traceService.enrichWithdrawalPage(withdrawalService.getManagementPage(request));
        page.getList().forEach(item -> reviewService.enrich(item, creatorUserId));
        return page;
    }
    @Override protected List<String> columns() {
        return List.of("提现ID", "提现编号", "申请人ID", "状态", "核验状态", "申请金额", "批准金额", "账户名",
                "银行卡号", "开户银行", "提交时间", "审核时间", "打款时间", "银行流水号",
                "申请人", "合作方", "来源返现笔数", "申请时可用余额", "开户支行", "审核人",
                "审核意见", "驳回原因", "打款登记人", "打款备注", "历史打款凭证文件ID");
    }
    @Override protected List<Object> toRow(WithdrawalRespVO item) {
        return List.of(value(item.getId()), value(item.getWithdrawalNo()), value(item.getApplicantUserId()),
                value(WithdrawalConstants.statusLabel(item.getStatus())), value(item.getVerificationStatus()), value(item.getApplicationAmount()),
                value(item.getApprovedAmount()), value(item.getAccountNameSnapshot()), value(item.getCardNumber()),
                value(item.getBankNameSnapshot()), value(item.getSubmittedAt()), value(item.getReviewedAt()),
                value(item.getPaidAt()), value(item.getBankTransactionNo()), value(item.getApplicantName()),
                value(item.getPartnerName()), value(item.getCashbackCount()), value(item.getAvailableBalanceSnapshot()),
                value(item.getBranchNameSnapshot()), value(item.getReviewedByName()), value(item.getReviewReason()),
                value(item.getRejectionReason()), value(item.getPaidByName()), value(item.getPayoutRemark()),
                value(item.getProofFileId()));
    }
    @Override protected String sheetName() { return "提现"; }

    private static Object value(Object value) {
        if (value instanceof LocalDateTime time) return DISPLAY_TIME.format(time);
        return value == null ? "" : value instanceof Long ? value.toString() : value;
    }
}
