package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadClaimPoolPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.LeadPendingRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.rule.LeadAssignmentRuleUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;

import java.util.List;

public interface LeadDispatchService {
    record TransferAttemptResult(boolean transferred, String reason) {
        public static TransferAttemptResult success() { return new TransferAttemptResult(true, null); }
        public static TransferAttemptResult invalidated(String reason) { return new TransferAttemptResult(false, reason); }
    }
    void start(LeadDO lead, Long specifiedSalesUserId, Long submitterUserId);
    List<cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO> getEligibleSalesUsers();
    List<cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO> getAssignableSalesUsers(Long sourceUserId);
    void notifyActivation(LeadDO lead);
    List<LeadPendingRespVO> getMyPending(Long userId);
    PageResult<LeadPendingRespVO> getClaimPoolPage(LeadClaimPoolPageReqVO reqVO, Long userId);
    void accept(Long leadId, Long userId);
    void reject(Long leadId, Long userId);
    void claim(Long leadId, Long userId);
    LeadAssignmentRuleRespVO getRule();
    void updateRule(LeadAssignmentRuleUpdateReqVO reqVO);
    void adminTransfer(Long leadId, Long salesUserId, Long operatorUserId);
    void adminTransfer(Long leadId, Long salesUserId, Long operatorUserId, String reason);
    TransferAttemptResult tryAdminTransfer(Long leadId, Long expectedOwnerUserId, Long salesUserId,
                                           Long operatorUserId, String reason);
    int processExpired();
    int processUnassignedRetries();
}
