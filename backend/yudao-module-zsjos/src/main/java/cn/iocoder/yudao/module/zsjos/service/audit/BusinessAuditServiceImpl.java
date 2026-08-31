package cn.iocoder.yudao.module.zsjos.service.audit;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.audit.BusinessAuditLogDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.audit.BusinessAuditLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationRequestLogMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.BusinessAuditPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo.ImpersonationAuditRespVO;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAuditOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    @TenantIgnore
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long begin(ZsjosAuditOperation operation) {
        HttpServletRequest request = currentRequest();
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        String operatorName = operatorId == null ? "系统" : SecurityFrameworkUtils.getLoginUserNickname();
        Long tenantId = TenantContextHolder.getTenantId();
        BusinessAuditLogDO log = new BusinessAuditLogDO();
        log.setTenantId(tenantId == null ? 0L : tenantId);
        log.setOperatorUserId(operatorId)
                .setOperatorNameSnapshot(operatorName == null ? "未知账号" : operatorName)
                .setOperatorRoleSnapshot(operation.sourceType())
                .setCategoryCode(operation.category()).setActionCode(operation.action())
                .setTargetType(operation.targetType()).setTargetId(operation.targetId())
                .setDetailJson("{}").setSourceIp(request == null ? null : ServletUtils.getClientIP(request))
                .setSourceType(operation.sourceType()).setTraceId(TracerUtils.getTraceId())
                .setRequestMethod(operation.requestMethod()).setRequestPath(operation.requestPath())
                .setResultStatus("STARTED").setOccurredAt(LocalDateTime.now());
        mapper.insert(log);
        return log.getId();
    }

    @Override
    @TenantIgnore
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void complete(Long auditId, boolean success, Integer resultCode, String resultMessage, long durationMs) {
        BusinessAuditLogDO update = new BusinessAuditLogDO().setId(auditId)
                .setResultStatus(success ? "SUCCESS" : "FAILURE")
                .setResultCode(resultCode).setResultMessage(sanitizeResultMessage(resultMessage))
                .setFinishedAt(LocalDateTime.now()).setDurationMs(durationMs);
        if (mapper.updateById(update) != 1) {
            throw exception(AUDIT_ACTION_INVALID);
        }
    }

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
                .setSourceType("EXPLICIT").setResultStatus("SUCCESS")
                .setResultCode(0).setOccurredAt(LocalDateTime.now()).setFinishedAt(LocalDateTime.now()));
    }

    @Override
    public PageResult<BusinessAuditRespVO> getPage(BusinessAuditPageReqVO reqVO) {
        return BeanUtils.toBean(mapper.selectPage(reqVO), BusinessAuditRespVO.class);
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

    private static String sanitizeResultMessage(String message) {
        if (message == null) return null;
        String value = message.replaceAll("(?i)(password|token|mobile|phone|bank.?card)\\s*[=:]\\s*[^,;\\s]+", "$1=[REDACTED]");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
