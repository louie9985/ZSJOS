package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeedbackFileUrlsTest {

    private final FileApi fileApi = mock(FileApi.class);

    @Test
    void batchesDistinctIdsWithoutSingleFileCalls() {
        when(fileApi.presignGetUrls(List.of(3L, 4L), null)).thenReturn(Map.of(3L, "a", 4L, "b"));
        assertEquals(Map.of(3L, "a", 4L, "b"), FeedbackFileUrls.resolve(fileApi, List.of(3L, 4L, 3L)));
        verify(fileApi).presignGetUrls(List.of(3L, 4L), null);
        verifyNoMoreInteractions(fileApi);
    }

    @Test
    void failedBatchRetriesEachDistinctIdAndKeepsHealthyFiles() {
        when(fileApi.presignGetUrls(List.of(3L, 4L), null)).thenThrow(new IllegalStateException("Unavailable"));
        when(fileApi.presignGetUrl(3L, null)).thenThrow(new IllegalStateException("Missing"));
        when(fileApi.presignGetUrl(4L, null)).thenReturn("fresh");
        var urls = FeedbackFileUrls.resolve(fileApi, List.of(3L, 4L, 3L));
        assertNull(urls.get(3L));
        assertEquals("fresh", urls.get(4L));
        verify(fileApi).presignGetUrls(List.of(3L, 4L), null);
        verify(fileApi).presignGetUrl(3L, null);
        verify(fileApi).presignGetUrl(4L, null);
        verifyNoMoreInteractions(fileApi);
    }

    @Test
    void emptyInputDoesNotContactInfra() {
        assertTrue(FeedbackFileUrls.resolve(fileApi, List.of()).isEmpty());
        verifyNoInteractions(fileApi);
    }
}
