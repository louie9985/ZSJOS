package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonContactClaimDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonContactClaimMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CONTACT_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonIdentityWriteServiceTest {
    @InjectMocks private PersonIdentityWriteService service;
    @Mock private PersonMapper personMapper;
    @Mock private PersonContactClaimMapper claimMapper;
    @Mock private PersonNumberService personNumberService;
    private final Map<String, PersonContactClaimDO> claims = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(personNumberService.next()).thenReturn("XY202608241430250001");
        lenient().doAnswer(invocation -> {
            String value = invocation.getArgument(1); String key = invocation.getArgument(2);
            claims.computeIfAbsent(value, ignored -> claim(value, null, key));
            return 1;
        }).when(claimMapper).reserve(eq(1L), anyString(), anyString());
        lenient().when(claimMapper.selectByValueForUpdate(eq(1L), anyString()))
                .thenAnswer(invocation -> claims.get(invocation.getArgument(1)));
    }

    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void createTrimsAndDeduplicatesSameContactAcrossFields() {
        doAnswer(invocation -> { ((PersonDO) invocation.getArgument(0)).setId(10L); return 1; })
                .when(personMapper).insert(any(PersonDO.class));

        PersonDO person = service.createNew("张三", " wx-id ", "wx-id", "active");

        assertEquals("wx-id", person.getMobile()); assertEquals("wx-id", person.getWechatId());
        assertEquals("XY202608241430250001", person.getPersonNo());
        verify(claimMapper, times(1)).reserve(eq(1L), eq("wx-id"), anyString());
        verify(claimMapper).bindReservations(eq(1L), anyString(), eq(10L));
    }

    @Test
    void resolveExistingLocksPersonAndBindsNewContact() {
        claims.put("13800138000", claim("13800138000", 10L, null));
        PersonDO existing = new PersonDO(); existing.setId(10L);
        when(personMapper.selectByIdForUpdate(10L, 1L)).thenReturn(existing);

        assertSame(existing, service.resolveOrCreate("张三", "13800138000", "WxCase", "active"));

        verify(claimMapper).reserve(eq(1L), eq("WxCase"), anyString());
        verify(claimMapper).bindReservations(eq(1L), anyString(), eq(10L));
        verify(personMapper, never()).insert(any(PersonDO.class));
        verify(personNumberService, never()).next();
    }

    @Test
    void caseSensitiveWechatValuesRemainDistinct() {
        doAnswer(invocation -> { ((PersonDO) invocation.getArgument(0)).setId(11L); return 1; })
                .when(personMapper).insert(any(PersonDO.class));

        service.createNew("李四", null, "WxCase", "active");

        assertTrue(claims.containsKey("WxCase"));
        assertFalse(claims.containsKey("wxcase"));
    }

    @Test
    void conflictingOwnersAreRejected() {
        claims.put("13800138000", claim("13800138000", 10L, null));
        claims.put("wx-id", claim("wx-id", 11L, null));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.resolveOrCreate("王五", "13800138000", "wx-id", "active"));

        assertEquals(LEAD_CONTACT_CONFLICT.getCode(), error.getCode());
        verify(personMapper, never()).insert(any(PersonDO.class));
    }

    @Test
    void updateReleasesContactsThatWereRemoved() {
        PersonDO person = new PersonDO(); person.setId(10L); person.setMobile("old-mobile"); person.setWechatId("old-wechat");
        when(personMapper.selectByIdForUpdate(10L, 1L)).thenReturn(person);

        service.update(10L, "新姓名", " new-mobile ", null);

        assertEquals("new-mobile", person.getMobile()); assertNull(person.getWechatId());
        verify(claimMapper).deleteStale(1L, 10L, java.util.List.of("new-mobile"));
    }

    private static PersonContactClaimDO claim(String value, Long personId, String key) {
        PersonContactClaimDO claim = new PersonContactClaimDO(); claim.setContactValue(value);
        claim.setPersonId(personId); claim.setReservationKey(key); return claim;
    }
}
