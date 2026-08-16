package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface LeadAgingPoolService {
    PageResult<LeadAgingPoolRespVO> getPage(LeadAgingPoolPageReqVO reqVO, Long userId);
    LeadAgingPoolRespVO get(Long cycleId, Long userId);
    Map<String, Long> getCounts(Long userId);
    LeadInboxFilterProfileRespVO getFilterProfile(Long userId);
    List<LeadAgingPoolCandidateRespVO> getCandidates(Long cycleId, Long userId);
    void assign(Long cycleId, Long userId, LeadAgingPoolAssignReqVO reqVO);
    void exit(Long cycleId, Long userId, LeadAgingPoolExitReqVO reqVO);
    int scanDue(LocalDateTime now);
    boolean tryEnterDueLead(Long leadId, LocalDateTime now);
    int clearInvalidCollaborators(LocalDateTime now);
    int emitAdvanceReminders(LocalDateTime now);
    int processPreQualificationNoProgress(LocalDateTime now);
    boolean canOperate(Long leadId, Long formalOwnerUserId, Long operatorUserId);
    void requireCanOperateForUpdate(Long leadId, Long formalOwnerUserId, Long operatorUserId);
    LeadAgingPoolCycleDO getActiveCycle(Long leadId);
    void markDealPending(Long leadId, Long salesUserId, LocalDateTime now);
    void handleOrderRejected(Long leadId, LocalDateTime now);
    void completeConversion(Long leadId, Long salesUserId, LocalDateTime now);
    void terminateForOwnerTransfer(Long leadId, Long newOwnerUserId, Long operatorUserId, LocalDateTime now);
    boolean canRead(Long leadId, Long userId);
    boolean canRead(LeadAgingPoolCycleDO cycle, Long userId);
}
