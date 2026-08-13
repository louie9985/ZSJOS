package cn.iocoder.yudao.module.zsjos.service.audit;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.audit.BusinessAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationRequestLogMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.ImpersonationAuditRespVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.AUDIT_ACTION_INVALID;

@Service
public class BusinessAuditServiceImpl implements BusinessAuditService {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "mobile", "phone", "wechat", "bankcard", "bank_card", "password", "token",
            "keyword", "filter", "content", "filecontent", "file_content");
    @Resource private BusinessAuditLogMapper mapper;
    @Resource private ImpersonationRequestLogMapper impersonationLogMapper;

    @Override
    public void record(String category, String action, String targetType, String targetId,
                       String operatorRoleSnapshot, Map<String, ?> safeDetails) {
        if (!AuditActionCatalog.contains(category, action) || hasSensitiveDetail(safeDetails)) {
            throw exception(AUDIT_ACTION_INVALID);
        }
        HttpServletRequest request = currentRequest();
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        String operatorName = operatorId == null ? "系统" : SecurityFrameworkUtils.getLoginUserNickname();
        mapper.insert(new BusinessAuditLogDO().setOperatorUserId(operatorId)
                .setOperatorNameSnapshot(operatorName == null ? "未知账号" : operatorName)
                .setOperatorRoleSnapshot(operatorRoleSnapshot == null ? "系统" : operatorRoleSnapshot)
                .setCategoryCode(category).setActionCode(action)
                .setTargetType(targetType).setTargetId(targetId)
                .setDetailJson(JsonUtils.toJsonString(safeDetails == null ? Map.of() : safeDetails))
                .setSourceIp(request == null ? null : ServletUtils.getClientIP(request))
                .setOccurredAt(LocalDateTime.now()));
    }

    @Override
    public PageResult<BusinessAuditRespVO> getPage(PageParam page, String actionCode, String targetType) {
        return BeanUtils.toBean(mapper.selectPage(page, actionCode, targetType), BusinessAuditRespVO.class);
    }

    @Override
    public PageResult<ImpersonationAuditRespVO> getImpersonationPage(PageParam page, Long sessionId) {
        return BeanUtils.toBean(impersonationLogMapper.selectPage(page, sessionId), ImpersonationAuditRespVO.class);
    }

    private static boolean hasSensitiveDetail(Map<String, ?> details) {
        if (details == null) return false;
        return details.keySet().stream().map(key -> key.replace("-", "_").toLowerCase(Locale.ROOT))
                .anyMatch(key -> SENSITIVE_KEYS.stream().anyMatch(key::contains));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
