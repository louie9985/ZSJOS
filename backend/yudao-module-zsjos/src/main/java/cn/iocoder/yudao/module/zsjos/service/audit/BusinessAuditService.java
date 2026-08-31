package cn.iocoder.yudao.module.zsjos.service.audit;

import java.util.Map;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.ImpersonationAuditRespVO;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAuditOperation;

public interface BusinessAuditService {
    Long begin(ZsjosAuditOperation operation);
    void complete(Long auditId, boolean success, Integer resultCode, String resultMessage, long durationMs);
    void record(String category, String action, String targetType, String targetId,
                String operatorRoleSnapshot, Map<String, ?> safeDetails);
    PageResult<BusinessAuditRespVO> getPage(BusinessAuditPageReqVO reqVO);
    PageResult<ImpersonationAuditRespVO> getImpersonationPage(PageParam page, Long sessionId);
}
