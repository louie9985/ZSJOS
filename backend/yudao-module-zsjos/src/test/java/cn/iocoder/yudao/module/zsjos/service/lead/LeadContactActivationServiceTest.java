package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadContactActivationServiceTest {
    @InjectMocks private LeadContactActivationService service;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadActivationMapper activationMapper;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private LeadDispatchService dispatchService;

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(9L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void emptyContactsDoNotQueryOrNotify() {
        assertThrows(ServiceException.class, () -> activate(" ", " ", "one"));
        verifyNoInteractions(personMapper, leadMapper, activationMapper, notifyEventPublisher);
    }

    @Test void noMatchDoesNotCreateAnything() {
        when(personMapper.selectDuplicateCandidates(null, "wx_test")).thenReturn(List.of());
        when(leadMapper.selectContactActivationLeads(List.of())).thenReturn(List.of());
        assertFalse(activate(null, "WX_Test", "one"));
        verifyNoInteractions(activationMapper, notifyEventPublisher, dispatchService);
    }

    @Test void allMatchesIncludeClosedOwnerlessAndEducationAndRetryOnlyOnce() {
        PersonDO first = new PersonDO(); first.setId(1L);
        PersonDO second = new PersonDO(); second.setId(2L);
        when(personMapper.selectDuplicateCandidates("13800000000", "wx_test")).thenReturn(List.of(first, second));
        LeadDO a = new LeadDO().setId(10L).setPersonId(1L).setStatus("closed").setOwnerUserId(30L);
        LeadDO b = new LeadDO().setId(20L).setPersonId(2L).setStatus("won").setOwnerIdentity("education").setOwnerUserId(40L);
        LeadDO c = new LeadDO().setId(21L).setPersonId(2L).setStatus("submitted");
        when(leadMapper.selectContactActivationLeads(List.of(1L, 2L))).thenReturn(List.of(b, a, a, c));
        for (LeadDO lead : List.of(a,b,c)) when(leadMapper.selectByIdForUpdate(lead.getId(), 9L)).thenReturn(lead);
        Map<String, LeadActivationDO> saved = new HashMap<>();
        when(activationMapper.selectByIdempotencyKey(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
        doAnswer(call -> { LeadActivationDO row = call.getArgument(0); saved.put(row.getIdempotencyKey(), row); return 1; })
                .when(activationMapper).insert(any(LeadActivationDO.class));

        assertTrue(activate("13800000000", "WX_Test", "one"));
        assertTrue(activate("13800000000", "wx_test", "one"));
        assertEquals(3, saved.size());
        for (LeadDO lead : List.of(a,b,c)) {
            verify(dispatchService).notifyActivation(lead);
            verify(notifyEventPublisher).publish(eq("zsjos.lead.activated"), eq(lead.getId()), anyString(), eq(5L), any(), anyMap());
        }
        assertEquals("closed", a.getStatus());
        assertEquals("won", b.getStatus());
        verify(leadMapper, never()).updateById(any(LeadDO.class));
    }

    @Test void typedIdentitySeparatesPartnerAndInternalRequests() {
        PersonDO person = new PersonDO(); person.setId(1L);
        when(personMapper.selectDuplicateCandidates(null, "wx")).thenReturn(List.of(person));
        LeadDO lead = new LeadDO().setId(10L).setPersonId(1L);
        when(leadMapper.selectContactActivationLeads(List.of(1L))).thenReturn(List.of(lead));
        when(leadMapper.selectByIdForUpdate(10L, 9L)).thenReturn(lead);
        activate(null, "wx", "same");
        service.activate(null, "wx", "same", 5L, "partner", 8L);
        ArgumentCaptor<LeadActivationDO> rows = ArgumentCaptor.forClass(LeadActivationDO.class);
        verify(activationMapper, times(2)).insert(rows.capture());
        assertNotEquals(rows.getAllValues().get(0).getIdempotencyKey(), rows.getAllValues().get(1).getIdempotencyKey());
        assertNull(rows.getAllValues().get(1).getSourceUserId());
        assertEquals(8L, rows.getAllValues().get(1).getPartnerId());
        verify(notifyEventPublisher).publish(eq("zsjos.lead.activated"), eq(10L), anyString(), eq(5L), any(),
                argThat(payload -> Integer.valueOf(2).equals(payload.get("operatorUserType"))));
        verify(notifyEventPublisher).publish(eq("zsjos.lead.activated"), eq(10L), anyString(), eq(5L), any(),
                argThat(payload -> Integer.valueOf(3).equals(payload.get("operatorUserType"))));
    }

    private boolean activate(String mobile, String wechat, String key) {
        return service.activate(mobile, wechat, key, 5L, "internal_new_media", null);
    }
}
