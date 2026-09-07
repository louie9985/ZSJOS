package cn.iocoder.yudao.module.infra.api.websocket;

import cn.iocoder.yudao.module.infra.service.websocket.WebSocketPresenceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class WebSocketPresenceApiImpl implements WebSocketPresenceApi {

    @Resource
    private WebSocketPresenceService webSocketPresenceService;

    @Override
    public Set<Long> getOnlineUserIds(Long tenantId, Integer userType) {
        return webSocketPresenceService.getOnlineUserIds(tenantId, userType);
    }

}
