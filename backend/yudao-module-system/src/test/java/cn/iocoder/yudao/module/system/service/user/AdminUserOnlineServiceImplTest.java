package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketPresenceApi;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_ONLINE_STATUS_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserOnlineServiceImplTest {

    private final WebSocketPresenceApi presenceApi = mock(WebSocketPresenceApi.class);
    private final AdminUserMapper userMapper = mock(AdminUserMapper.class);
    private final AdminUserOnlineServiceImpl service = new AdminUserOnlineServiceImpl(presenceApi, userMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsOnlyEnabledAdminUsersAndDeduplicatesConnections() {
        when(presenceApi.getOnlineUserIds(1L, UserTypeEnum.ADMIN.getValue())).thenReturn(Set.of(10L, 20L));
        when(userMapper.selectByIds(Set.of(10L, 20L))).thenReturn(List.of(
                new AdminUserDO().setId(10L).setStatus(CommonStatusEnum.ENABLE.getStatus()),
                new AdminUserDO().setId(20L).setStatus(CommonStatusEnum.DISABLE.getStatus())));

        var result = service.getOnlineStatus(List.of(10L, 20L, 30L));

        assertTrue(result.getAvailable());
        assertEquals(Set.of(10L), result.getOnlineUserIds());
        assertEquals(1L, result.getOnlineCount());
        assertNotNull(result.getObservedAt());
    }

    @Test
    void statusQueryDegradesButRequiredQueryFailsClearly() {
        when(presenceApi.getOnlineUserIds(1L, UserTypeEnum.ADMIN.getValue()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        var status = service.getOnlineStatus(List.of(10L));
        assertFalse(status.getAvailable());
        assertNull(status.getOnlineCount());
        assertTrue(status.getOnlineUserIds().isEmpty());

        ServiceException exception = assertThrows(ServiceException.class, service::getRequiredOnlineUserIds);
        assertEquals(USER_ONLINE_STATUS_UNAVAILABLE.getCode(), exception.getCode());
    }

}
