package cn.iocoder.yudao.module.zsjos.service.production;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketCommandMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductionTicketCommandServiceTest {
    @InjectMocks private ProductionTicketCommandService service;
    @Mock private ProductionTicketCommandMapper mapper;

    @Test
    void firstCommandIsRegisteredAndCompleted() {
        var command = command("claim", 10L, 2, "fingerprint");
        when(mapper.complete(20L, "key-1", "true")).thenReturn(1);

        var claim = service.begin("key-1", command, Boolean.class);
        service.complete("key-1", 20L, true);

        assertTrue(claim.created());
        assertNull(claim.result());
        verify(mapper).insert(argThat((ProductionTicketCommandDO row) -> row.getOperatorUserId().equals(20L)
                && row.getTicketId().equals(10L) && !row.getCompleted()));
        verify(mapper).complete(20L, "key-1", "true");
    }

    @Test
    void completedExactRetryReplaysStoredResult() {
        when(mapper.selectByOperatorAndKey(20L, "key-1")).thenReturn(row(true, "true"));

        var claim = service.begin("key-1", command("claim", 10L, 2, "fingerprint"), Boolean.class);

        assertFalse(claim.created());
        assertTrue(claim.result());
        verify(mapper, never()).insert(any(ProductionTicketCommandDO.class));
    }

    @Test
    void changedVersionOrIncompleteCommandConflicts() {
        when(mapper.selectByOperatorAndKey(20L, "key-1")).thenReturn(row(true, "true"));
        ServiceException changed = assertThrows(ServiceException.class,
                () -> service.begin("key-1", command("claim", 10L, 3, "other"), Boolean.class));
        assertEquals(1_900_013_014, changed.getCode());

        when(mapper.selectByOperatorAndKey(20L, "key-2")).thenReturn(row(false, null));
        ServiceException incomplete = assertThrows(ServiceException.class,
                () -> service.begin("key-2", command("claim", 10L, 2, "fingerprint"), Boolean.class));
        assertEquals(1_900_013_014, incomplete.getCode());
    }

    @Test
    void duplicateInsertRaceReplaysWinner() {
        when(mapper.selectByOperatorAndKey(20L, "key-1")).thenReturn(null, row(true, "true"));
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(ProductionTicketCommandDO.class));

        var claim = service.begin("key-1", command("claim", 10L, 2, "fingerprint"), Boolean.class);

        assertFalse(claim.created());
        assertTrue(claim.result());
    }

    private ProductionTicketCommandService.Command command(String action, Long ticketId, Integer version,
                                                             String fingerprint) {
        return new ProductionTicketCommandService.Command(action, null, ticketId, version, 20L, fingerprint);
    }

    private ProductionTicketCommandDO row(boolean completed, String result) {
        return new ProductionTicketCommandDO().setOperatorUserId(20L).setIdempotencyKey("key-1")
                .setActionType("claim").setTicketId(10L).setExpectedVersion(2)
                .setRequestFingerprint("fingerprint").setCompleted(completed).setResultJson(result);
    }
}
