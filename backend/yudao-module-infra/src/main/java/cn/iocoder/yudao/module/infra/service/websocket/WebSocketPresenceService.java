package cn.iocoder.yudao.module.infra.service.websocket;

import java.util.Set;

public interface WebSocketPresenceService {

    void touch(Long tenantId, Integer userType, Long userId, String sessionId);

    void remove(Long tenantId, Integer userType, Long userId, String sessionId);

    Set<Long> getOnlineUserIds(Long tenantId, Integer userType);

}
