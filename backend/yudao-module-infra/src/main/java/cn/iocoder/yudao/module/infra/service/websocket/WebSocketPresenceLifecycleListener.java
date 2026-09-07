package cn.iocoder.yudao.module.infra.service.websocket;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.websocket.core.session.WebSocketSessionLifecycleListener;
import cn.iocoder.yudao.framework.websocket.core.util.WebSocketFrameworkUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketPresenceLifecycleListener implements WebSocketSessionLifecycleListener {

    @Resource
    private WebSocketPresenceService webSocketPresenceService;

    @Override
    public void onConnected(WebSocketSession session) {
        touch(session);
    }

    @Override
    public void onHeartbeat(WebSocketSession session) {
        touch(session);
    }

    @Override
    public void onDisconnected(WebSocketSession session) {
        LoginUser loginUser = WebSocketFrameworkUtils.getLoginUser(session);
        if (!isTrackable(loginUser)) {
            return;
        }
        webSocketPresenceService.remove(loginUser.getTenantId(), loginUser.getUserType(), loginUser.getId(),
                session.getId());
    }

    private void touch(WebSocketSession session) {
        LoginUser loginUser = WebSocketFrameworkUtils.getLoginUser(session);
        if (!isTrackable(loginUser)) {
            return;
        }
        webSocketPresenceService.touch(loginUser.getTenantId(), loginUser.getUserType(), loginUser.getId(),
                session.getId());
    }

    private static boolean isTrackable(LoginUser loginUser) {
        return loginUser != null && loginUser.getTenantId() != null && loginUser.getUserType() != null
                && loginUser.getId() != null;
    }

}
