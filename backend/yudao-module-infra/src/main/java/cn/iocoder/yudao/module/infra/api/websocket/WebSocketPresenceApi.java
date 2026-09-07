package cn.iocoder.yudao.module.infra.api.websocket;

import java.util.Set;

/**
 * WebSocket 在线状态 API。
 */
public interface WebSocketPresenceApi {

    /**
     * 获得指定租户、用户类型当前存在活跃 WebSocket 连接的用户编号。
     */
    Set<Long> getOnlineUserIds(Long tenantId, Integer userType);

}
