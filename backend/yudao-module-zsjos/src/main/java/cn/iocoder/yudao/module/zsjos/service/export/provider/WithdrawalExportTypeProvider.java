package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WithdrawalExportTypeProvider extends AbstractPagedExportTypeProvider<WithdrawalPageReqVO, WithdrawalRespVO> {

    @Resource
    private WithdrawalService withdrawalService;

    @Override public String getType() { return "withdrawal"; }
    @Override public String getCreatePermission() { return "zsjos:export:withdrawal"; }
    @Override protected Class<WithdrawalPageReqVO> requestType() { return WithdrawalPageReqVO.class; }
    @Override protected PageResult<WithdrawalRespVO> getPage(WithdrawalPageReqVO request, Long creatorUserId) {
        return withdrawalService.getPage(request, null);
    }
    @Override protected List<String> columns() {
        return List.of("提现ID", "提现编号", "申请人ID", "状态", "核验状态", "申请金额", "批准金额", "账户名",
                "银行卡号", "开户银行", "提交时间", "审核时间", "打款时间", "银行流水号");
    }
    @Override protected List<Object> toRow(WithdrawalRespVO item) {
        return List.of(value(item.getId()), value(item.getWithdrawalNo()), value(item.getApplicantUserId()),
                value(item.getStatus()), value(item.getVerificationStatus()), value(item.getApplicationAmount()),
                value(item.getApprovedAmount()), value(item.getAccountNameSnapshot()), value(item.getMaskedCardNumber()),
                value(item.getBankNameSnapshot()), value(item.getSubmittedAt()), value(item.getReviewedAt()),
                value(item.getPaidAt()), value(item.getBankTransactionNo()));
    }
    @Override protected String sheetName() { return "提现"; }

    private static Object value(Object value) {
        return value == null ? "" : value instanceof Long ? value.toString() : value;
    }
}
