package cn.iocoder.yudao.module.zsjos.service.audit;

import java.util.Map;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.ImpersonationAuditRespVO;

public interface BusinessAuditService {
    void record(String category, String action, String targetType, String targetId,
                String operatorRoleSnapshot, Map<String, ?> safeDetails);
    PageResult<BusinessAuditRespVO> getPage(PageParam page, String actionCode, String targetType);
    PageResult<ImpersonationAuditRespVO> getImpersonationPage(PageParam page, Long sessionId);
}
