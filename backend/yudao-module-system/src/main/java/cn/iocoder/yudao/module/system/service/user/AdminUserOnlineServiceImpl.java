package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketPresenceApi;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserOnlineStatusRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_ONLINE_STATUS_UNAVAILABLE;

@Service
@Slf4j
public class AdminUserOnlineServiceImpl implements AdminUserOnlineService {

    private final WebSocketPresenceApi webSocketPresenceApi;
    private final AdminUserMapper userMapper;

    public AdminUserOnlineServiceImpl(WebSocketPresenceApi webSocketPresenceApi, AdminUserMapper userMapper) {
        this.webSocketPresenceApi = webSocketPresenceApi;
        this.userMapper = userMapper;
    }

    @Override
    public Set<Long> getRequiredOnlineUserIds() {
        try {
            return getEnabledOnlineUserIds();
        } catch (RuntimeException ex) {
            log.warn("[getRequiredOnlineUserIds][查询租户({})在线员工失败]", TenantContextHolder.getTenantId(), ex);
            throw exception(USER_ONLINE_STATUS_UNAVAILABLE);
        }
    }

    @Override
    public UserOnlineStatusRespVO getOnlineStatus(Collection<Long> requestedUserIds) {
        LocalDateTime observedAt = LocalDateTime.now();
        try {
            Set<Long> onlineUserIds = getEnabledOnlineUserIds();
            Set<Long> requestedOnlineUserIds = new LinkedHashSet<>();
            if (requestedUserIds != null && !requestedUserIds.isEmpty()) {
                for (Long userId : requestedUserIds) {
                    if (onlineUserIds.contains(userId)) {
                        requestedOnlineUserIds.add(userId);
                    }
                }
            }
            return new UserOnlineStatusRespVO(true, requestedOnlineUserIds, (long) onlineUserIds.size(), observedAt);
        } catch (RuntimeException ex) {
            log.warn("[getOnlineStatus][查询租户({})在线员工失败]", TenantContextHolder.getTenantId(), ex);
            return new UserOnlineStatusRespVO(false, Collections.emptySet(), null, observedAt);
        }
    }

    private Set<Long> getEnabledOnlineUserIds() {
        Set<Long> connectedUserIds = webSocketPresenceApi.getOnlineUserIds(
                TenantContextHolder.getRequiredTenantId(), UserTypeEnum.ADMIN.getValue());
        if (connectedUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<AdminUserDO> users = userMapper.selectByIds(connectedUserIds);
        Set<Long> result = new LinkedHashSet<>();
        for (AdminUserDO user : users) {
            if (CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
                result.add(user.getId());
            }
        }
        return result;
    }

}
