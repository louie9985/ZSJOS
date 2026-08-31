package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo.ImpersonationSessionRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation.ImpersonationRequestLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.impersonation.ImpersonationSessionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationRequestLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.impersonation.ImpersonationSessionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ImpersonationServiceImpl implements ImpersonationService {
    static final int IDLE_MINUTES = 30;
    @Resource private ImpersonationSessionMapper sessionMapper;
    @Resource private ImpersonationRequestLogMapper requestLogMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImpersonationSessionRespVO start(Long administratorUserId, Long targetUserId, String reason) {
        if (administratorUserId.equals(targetUserId)) throw exception(IMPERSONATION_TARGET_INVALID);
        AdminUserRespDTO administrator = adminUserApi.getUser(administratorUserId);
        AdminUserRespDTO target = adminUserApi.getUser(targetUserId);
        if (administrator == null || target == null
                || !CommonStatusEnum.ENABLE.getStatus().equals(target.getStatus())) {
            throw exception(IMPERSONATION_TARGET_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        ImpersonationSessionDO session = new ImpersonationSessionDO()
                .setAdministratorUserId(administratorUserId).setAdministratorNameSnapshot(administrator.getNickname())
                .setTargetUserId(targetUserId).setTargetNameSnapshot(target.getNickname()).setReason(reason.trim())
                .setStatus("active").setStartedAt(now).setLastActiveAt(now).setVersion(0);
        sessionMapper.insert(session);
        auditService.record("impersonation", "impersonation.start", "impersonation_session",
                session.getId().toString(), "administrator", Map.of("targetUserId", targetUserId));
        return BeanUtils.toBean(session, ImpersonationSessionRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void end(Long administratorUserId, Long sessionId, String reason) {
        ImpersonationSessionDO session = sessionMapper.selectActive(sessionId, administratorUserId);
        if (session == null) throw exception(IMPERSONATION_SESSION_INVALID);
        if (sessionMapper.close(sessionId, session.getVersion(), "ended", LocalDateTime.now(), reason) != 1) {
            throw exception(IMPERSONATION_SESSION_INVALID);
        }
        auditService.record("impersonation", "impersonation.end", "impersonation_session",
                sessionId.toString(), "administrator", Map.of("targetUserId", session.getTargetUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImpersonationContext useReadSession(Long administratorUserId, Long sessionId, String method, String path) {
        ImpersonationSessionDO session = sessionMapper.selectActive(sessionId, administratorUserId);
        LocalDateTime now = LocalDateTime.now();
        if (session == null || session.getLastActiveAt().isBefore(now.minusMinutes(IDLE_MINUTES))) {
            throw exception(IMPERSONATION_SESSION_INVALID);
        }
        AdminUserRespDTO target = adminUserApi.getUser(session.getTargetUserId());
        if (target == null || !CommonStatusEnum.ENABLE.getStatus().equals(target.getStatus())) {
            throw exception(IMPERSONATION_SESSION_INVALID);
        }
        if (sessionMapper.touch(sessionId, session.getVersion(), now) != 1) {
            throw exception(IMPERSONATION_SESSION_INVALID);
        }
        requestLogMapper.insert(new ImpersonationRequestLogDO().setSessionId(sessionId)
                .setAdministratorUserId(administratorUserId).setTargetUserId(session.getTargetUserId())
                .setHttpMethod(method).setRequestPath(path).setOccurredAt(now));
        return new ImpersonationContext(sessionId, session.getTargetUserId(),
                session.getTargetNameSnapshot(), target.getDeptId());
    }

    @Override
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "impersonation.expire-idle", targetType = "impersonation-session")
    @Transactional(rollbackFor = Exception.class)
    public int expireIdleSessions() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ImpersonationSessionDO session : sessionMapper.selectIdle(now.minusMinutes(IDLE_MINUTES))) {
            count += sessionMapper.close(session.getId(), session.getVersion(), "expired", now, "idle_timeout");
        }
        return count;
    }
}
