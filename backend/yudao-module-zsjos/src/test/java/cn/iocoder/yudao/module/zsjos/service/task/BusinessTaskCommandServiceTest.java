package cn.iocoder.yudao.module.zsjos.service.task;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessTaskCommandServiceTest {
    @InjectMocks private BusinessTaskCommandService service;
    @Mock private BusinessTaskMapper mapper;

    @Test
    void createReturnsExistingTaskForSameIdempotencyKey() {
        BusinessTaskDO existing = new BusinessTaskDO(); existing.setId(77L);
        when(mapper.selectByIdempotencyKey("stable-key")).thenReturn(existing);
        BusinessTaskCreateCommand command = new BusinessTaskCreateCommand("type", "biz", 1L, 2L,
                "title", null, "OPEN", null, null, null, "stable-key");

        assertEquals(77L, service.create(command));
        verify(mapper, never()).insert(any(BusinessTaskDO.class));
    }

    @Test
    void createReturnsRacingTaskAfterIdempotencyConflict() {
        BusinessTaskDO raced = new BusinessTaskDO(); raced.setId(88L);
        when(mapper.selectByIdempotencyKey("racing-key")).thenReturn(null, raced);
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insert(any(BusinessTaskDO.class));
        BusinessTaskCreateCommand command = new BusinessTaskCreateCommand("type", "biz", 1L, 2L,
                "title", null, "OPEN", null, null, null, "racing-key");

        assertEquals(88L, service.create(command));
        verify(mapper).insert(any(BusinessTaskDO.class));
    }
}
