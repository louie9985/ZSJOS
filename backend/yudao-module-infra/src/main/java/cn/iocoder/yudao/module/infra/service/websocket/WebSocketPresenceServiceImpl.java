package cn.iocoder.yudao.module.infra.service.websocket;

import cn.iocoder.yudao.module.infra.dal.redis.websocket.WebSocketPresenceRedisDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Set;

@Service
public class WebSocketPresenceServiceImpl implements WebSocketPresenceService {

    private final WebSocketPresenceRedisDAO presenceRedisDAO;
    private final Clock clock;

    @Autowired
    public WebSocketPresenceServiceImpl(WebSocketPresenceRedisDAO presenceRedisDAO) {
        this(presenceRedisDAO, Clock.systemUTC());
    }

    WebSocketPresenceServiceImpl(WebSocketPresenceRedisDAO presenceRedisDAO, Clock clock) {
        this.presenceRedisDAO = presenceRedisDAO;
        this.clock = clock;
    }

    @Override
    public void touch(Long tenantId, Integer userType, Long userId, String sessionId) {
        presenceRedisDAO.touch(tenantId, userType, userId, sessionId, clock.millis());
    }

    @Override
    public void remove(Long tenantId, Integer userType, Long userId, String sessionId) {
        presenceRedisDAO.remove(tenantId, userType, userId, sessionId);
    }

    @Override
    public Set<Long> getOnlineUserIds(Long tenantId, Integer userType) {
        return presenceRedisDAO.getOnlineUserIds(tenantId, userType, clock.millis());
    }

}
