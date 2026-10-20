package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.publicseapool.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadPublicSeaPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LeadPublicSeaPoolService {
    PageResult<LeadPublicSeaPoolRespVO> getPage(LeadPublicSeaPoolPageReqVO reqVO, Long userId);
    LeadPublicSeaPoolRespVO get(Long cycleId, Long userId);
    Map<String, Long> getCounts(Long userId);
    LeadInboxFilterProfileRespVO getFilterProfile(Long userId);
    List<LeadPublicSeaPoolCandidateRespVO> getCandidates(Long cycleId, Long userId);
    void assign(Long cycleId, Long userId, LeadPublicSeaPoolAssignReqVO reqVO);
    void exit(Long cycleId, Long userId, LeadPublicSeaPoolExitReqVO reqVO);
    int scanDue(LocalDateTime now);
    boolean tryEnterDueLead(Long leadId, LocalDateTime now);
    void enterManually(Long leadId, Long collaboratorUserId, Long operatorUserId, String reason,
                       String idempotencyKey);
    boolean canEnterManually(Long leadId);
    int clearInvalidCollaborators(LocalDateTime now);
    int emitAdvanceReminders(LocalDateTime now);
    boolean canOperate(Long leadId, Long formalOwnerUserId, Long operatorUserId);
    boolean canRequestTransfer(LeadPublicSeaPoolCycleDO cycle, Long userId);
    void requireCanOperateForUpdate(Long leadId, Long formalOwnerUserId, Long operatorUserId);
    LeadPublicSeaPoolCycleDO getActiveCycle(Long leadId);
    boolean hasCompatibilityPublicSeaPoolRecord(Long leadId);
    void markDealPending(Long leadId, Long salesUserId, LocalDateTime now);
    void handleOrderRejected(Long leadId, LocalDateTime now);
    void completeConversion(Long leadId, Long salesUserId, LocalDateTime now);
    void terminateForOwnerTransfer(Long leadId, Long newOwnerUserId, Long operatorUserId, LocalDateTime now);
    void terminateForOwnerTransfer(Long leadId, Long newOwnerUserId, Long operatorUserId,
                                   LocalDateTime now, String reason);
    boolean canRead(Long leadId, Long userId);
    boolean canRead(LeadPublicSeaPoolCycleDO cycle, Long userId);
    java.util.Set<Long> filterReadableLeadIds(java.util.Collection<Long> leadIds, Long userId);
}
