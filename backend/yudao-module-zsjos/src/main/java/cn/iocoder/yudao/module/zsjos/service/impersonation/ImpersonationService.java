package cn.iocoder.yudao.module.zsjos.service.impersonation;

import cn.iocoder.yudao.module.zsjos.controller.admin.impersonation.vo.ImpersonationSessionRespVO;

public interface ImpersonationService {
    ImpersonationSessionRespVO start(Long administratorUserId, Long targetUserId, String reason);
    void end(Long administratorUserId, Long sessionId, String reason);
    ImpersonationContext useReadSession(Long administratorUserId, Long sessionId, String method, String path);
    int expireIdleSessions();

    record ImpersonationContext(Long sessionId, Long targetUserId, String targetName, Long targetDeptId) {}
}
