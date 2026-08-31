package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO;

public interface LeadTransferRequestService {
    Long create(Long cycleId, Long requesterUserId, LeadTransferRequestCreateReqVO request);
    void handleProcessResult(String processInstanceId, Integer processStatus, String reason);
}
