package cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import cn.iocoder.yudao.module.zsjos.service.mediascreen.MediaScreenQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MediaScreenControllerTest {

    private final MediaScreenProperties properties = new MediaScreenProperties();
    private final MediaScreenController controller = new MediaScreenController(
            mock(MediaScreenQueryService.class), properties);

    @Test
    void rejectsInvalidPartTimerFlagWithStableBadRequestCode() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.stats(1L, 2));

        assertEquals(400, error.getCode());
        assertEquals("includePartTimers 只能为 0 或 1", error.getMessage());
    }

    @Test
    void rejectsFutureHistoryDateWithStableBadRequestCode() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.history(1L, LocalDate.now().plusDays(1), 0));

        assertEquals(400, error.getCode());
        assertEquals("历史日期超出允许范围", error.getMessage());
    }

    @Test
    void rejectsHistoryDateOutsideConfiguredWindow() {
        properties.getLimits().setMaxHistoryDays(30);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.history(1L, LocalDate.now().minusDays(31), 0));

        assertEquals(400, error.getCode());
    }
}
