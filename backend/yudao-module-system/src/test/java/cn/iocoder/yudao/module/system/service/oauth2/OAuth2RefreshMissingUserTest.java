package cn.iocoder.yudao.module.system.service.oauth2;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.*;
import cn.iocoder.yudao.module.system.dal.mysql.oauth2.*;
import cn.iocoder.yudao.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2RefreshMissingUserTest {
    @InjectMocks private OAuth2TokenServiceImpl service;
    @Mock private OAuth2AccessTokenMapper oauth2AccessTokenMapper;
    @Mock private OAuth2RefreshTokenMapper oauth2RefreshTokenMapper;
    @Mock private OAuth2AccessTokenRedisDAO oauth2AccessTokenRedisDAO;
    @Mock private OAuth2ClientService oauth2ClientService;
    @Mock private AdminUserService adminUserService;

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(1L);
        OAuth2RefreshTokenDO refresh = new OAuth2RefreshTokenDO().setId(11L)
                .setUserId(7L).setUserType(UserTypeEnum.ADMIN.getValue()).setClientId("fixture")
                .setRefreshToken("fixture-refresh").setExpiresTime(LocalDateTime.now().plusHours(1));
        refresh.setTenantId(1L);
        when(oauth2RefreshTokenMapper.selectByRefreshToken("fixture-refresh")).thenReturn(refresh);
        when(oauth2ClientService.validOAuthClientFromCache("fixture"))
                .thenReturn(new OAuth2ClientDO().setClientId("fixture").setAccessTokenValiditySeconds(60));
    }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test void missingAdminRevokesOnlyTheCurrentRefreshFamily() {
        when(oauth2AccessTokenMapper.selectListByRefreshToken("fixture-refresh"))
                .thenReturn(List.of(new OAuth2AccessTokenDO().setId(12L).setAccessToken("fixture-access")));
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.refreshAccessToken("fixture-refresh", "fixture"));
        assertEquals(401, error.getCode());
        verify(oauth2RefreshTokenMapper).deleteById(11L);
        verify(oauth2AccessTokenMapper).deleteByIds(java.util.Set.of(12L));
        verify(oauth2AccessTokenRedisDAO).deleteList(java.util.Set.of("fixture-access"));
        verify(oauth2AccessTokenRedisDAO).delete("fixture-refresh");
        verify(oauth2AccessTokenMapper, never()).insert(any(OAuth2AccessTokenDO.class));
    }

    @Test void userDatabaseFailureIsNotMisreportedAsLoggedOut() {
        var failure = new DataAccessResourceFailureException("fixture database unavailable");
        when(adminUserService.getUser(7L)).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.refreshAccessToken("fixture-refresh", "fixture")));
        verifyNoInteractions(oauth2AccessTokenRedisDAO);
        verify(oauth2RefreshTokenMapper, never()).deleteById(anyLong());
        verify(oauth2AccessTokenMapper, never()).deleteByIds(anyCollection());
    }

    @Test void redisFailureDuringRevocationRemainsAnInfrastructureFailure() {
        when(oauth2AccessTokenMapper.selectListByRefreshToken("fixture-refresh"))
                .thenReturn(List.of(new OAuth2AccessTokenDO().setId(12L).setAccessToken("fixture-access")));
        var failure = new RedisConnectionFailureException("fixture Redis unavailable");
        doThrow(failure).when(oauth2AccessTokenRedisDAO).deleteList(anyCollection());
        assertSame(failure, assertThrows(RedisConnectionFailureException.class,
                () -> service.refreshAccessToken("fixture-refresh", "fixture")));
    }
}
