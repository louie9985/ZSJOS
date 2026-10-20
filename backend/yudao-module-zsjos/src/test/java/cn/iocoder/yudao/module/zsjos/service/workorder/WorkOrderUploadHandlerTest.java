package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderFileRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkOrderUploadHandlerTest {
    private final WorkOrderService service = mock(WorkOrderService.class);

    @Test
    void exact100MbUsesOneBufferAndOversizeDoesNotOpenStream() throws Exception {
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(service, new WorkOrderUploadProperties());
        MultipartFile file = mock(MultipartFile.class);
        long limit = WorkOrderUploadProperties.MAX_FILE_BYTES;
        when(file.getSize()).thenReturn(limit);
        when(file.getInputStream()).thenReturn(zeroStream(limit));
        handler.upload(file, 1L);
        verify(service).upload(argThat(bytes -> bytes.length == limit), isNull(), isNull(), eq(1L));
        clearInvocations(service);
        reset(file);
        when(file.getSize()).thenReturn(limit + 1);
        assertEquals(WorkOrderUploadErrorCodes.FILE_SIZE_INVALID.getCode(),
                assertThrows(ServiceException.class, () -> handler.upload(file, 1L)).getCode());
        verify(file, never()).getInputStream();
    }

    @Test
    void emptyAndNegativeSizeNeverOpenStream() throws Exception {
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(service, new WorkOrderUploadProperties());
        for (long size : new long[]{0, -1}) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getSize()).thenReturn(size);
            assertThrows(ServiceException.class, () -> handler.upload(file, 1L));
            verify(file, never()).getInputStream();
        }
        verifyNoInteractions(service);
    }

    @Test
    void budgetDeniesImmediatelyAndRemainsReservedDuringStorage() throws Exception {
        assertSaturation(2, 10, 5, 5, 1); // request count
        assertSaturation(2, 10, 7, 0, 4); // aggregate bytes, even when a request slot remains
    }

    private void assertSaturation(int slots, long budget, int firstSize, int secondSize, int rejectedSize) throws Exception {
        WorkOrderUploadProperties properties = new WorkOrderUploadProperties();
        properties.setMaxConcurrentRequests(slots);
        properties.setMaxInFlightBytes(budget);
        WorkOrderService storage = mock(WorkOrderService.class);
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(storage, properties);
        CountDownLatch entered = new CountDownLatch(secondSize == 0 ? 1 : 2);
        CountDownLatch release = new CountDownLatch(1);
        when(storage.upload(any(), any(), any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("storage test timed out");
            return new WorkOrderFileRespVO();
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> handler.upload(file(firstSize), 1L));
            var second = secondSize == 0 ? null : executor.submit(() -> handler.upload(file(secondSize), 1L));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                MultipartFile denied = mock(MultipartFile.class);
                when(denied.getSize()).thenReturn((long) rejectedSize);
                assertEquals(WorkOrderUploadErrorCodes.UPLOAD_BUSY.getCode(),
                        assertThrows(ServiceException.class, () -> handler.upload(denied, 1L)).getCode());
                verify(denied, never()).getInputStream();
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
            if (second != null) second.get(5, TimeUnit.SECONDS);
        }
        assertNotNull(handler.upload(file((int) budget), 1L));
    }

    @Test
    void openingReadingClosingAndStorageFailuresAllReleaseTheBudget() throws Exception {
        WorkOrderUploadProperties properties = new WorkOrderUploadProperties();
        properties.setMaxInFlightBytes(1);
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(service, properties);
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getSize()).thenReturn(1L);
        when(broken.getInputStream()).thenThrow(new IOException("open failed"));
        assertThrows(IOException.class, () -> handler.upload(broken, 1L));
        handler.upload(file(1), 1L);

        doReturn(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
        }).when(broken).getInputStream();
        assertThrows(IOException.class, () -> handler.upload(broken, 1L));
        handler.upload(file(1), 1L);

        doReturn(new ByteArrayInputStream(new byte[1]) {
            @Override public void close() throws IOException { throw new IOException("close failed"); }
        }).when(broken).getInputStream();
        assertThrows(IOException.class, () -> handler.upload(broken, 1L));
        handler.upload(file(1), 1L);

        when(service.upload(any(), any(), any(), any())).thenThrow(new IllegalStateException("storage failed"))
                .thenThrow(new AssertionError("storage error")).thenReturn(new WorkOrderFileRespVO());
        assertThrows(IllegalStateException.class, () -> handler.upload(file(1), 1L));
        assertThrows(AssertionError.class, () -> handler.upload(file(1), 1L));
        assertNotNull(handler.upload(file(1), 1L));
    }

    @Test
    void truncatedAndOverstatedStreamsCannotEscapeTheirReservation() throws Exception {
        WorkOrderUploadProperties properties = new WorkOrderUploadProperties();
        properties.setMaxInFlightBytes(2);
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(service, properties);
        MultipartFile mismatch = mock(MultipartFile.class);
        when(mismatch.getSize()).thenReturn(2L);
        when(mismatch.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[1]), new ByteArrayInputStream(new byte[3]));
        assertThrows(ServiceException.class, () -> handler.upload(mismatch, 1L));
        assertThrows(ServiceException.class, () -> handler.upload(mismatch, 1L));
        verifyNoInteractions(service);
        handler.upload(file(2), 1L);
    }

    @Test
    void invalidBudgetConfigurationIsRejected() {
        WorkOrderUploadProperties properties = new WorkOrderUploadProperties();
        properties.setMaxConcurrentRequests(3);
        assertThrows(IllegalArgumentException.class, () -> new WorkOrderUploadHandler(service, properties));
        properties.setMaxConcurrentRequests(2);
        properties.setMaxInFlightBytes(WorkOrderUploadProperties.MAX_FILE_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () -> new WorkOrderUploadHandler(service, properties));
    }

    @Test
    void cancelledReadReleasesBudgetWithoutCallingStorage() throws Exception {
        var properties = new WorkOrderUploadProperties();
        properties.setMaxConcurrentRequests(1);
        properties.setMaxInFlightBytes(1);
        var handler = new WorkOrderUploadHandler(service, properties);
        var cancelled = mock(MultipartFile.class);
        when(cancelled.getSize()).thenReturn(1L);
        when(cancelled.getInputStream()).thenReturn(new InputStream() {
            @Override public int read() throws IOException {
                throw new java.io.InterruptedIOException("fixture cancelled upload");
            }
        });
        assertThrows(java.io.InterruptedIOException.class, () -> handler.upload(cancelled, 1L));
        verifyNoInteractions(service);
        handler.upload(file(1), 1L);
        verify(service).upload(any(), any(), any(), eq(1L));
    }

    @Test
    void repeatedFullBudgetUploadsRemainBoundedInControlledJvm() throws Exception {
        var handler = new WorkOrderUploadHandler(service, new WorkOrderUploadProperties());
        var pools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).toList();
        pools.forEach(java.lang.management.MemoryPoolMXBean::resetPeakUsage);
        for (int attempt = 0; attempt < 12; attempt++) {
            MultipartFile file = mock(MultipartFile.class);
            when(file.getSize()).thenReturn(WorkOrderUploadProperties.MAX_FILE_BYTES);
            when(file.getInputStream()).thenReturn(zeroStream(WorkOrderUploadProperties.MAX_FILE_BYTES));
            handler.upload(file, 1L);
            verify(service).upload(argThat(bytes -> bytes.length == WorkOrderUploadProperties.MAX_FILE_BYTES),
                    isNull(), isNull(), eq(1L));
            // Mockito 的调用记录不能持有生产存储已释放的缓冲区。
            clearInvocations(service);
        }
        assertSaturation(2, WorkOrderUploadProperties.MAX_FILE_BYTES,
                50 * 1024 * 1024, 50 * 1024 * 1024, 1);
        long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        System.out.printf("UPLOAD_FIXTURE iterations=12 concurrent=2 reservedBytes=%d heapPoolPeakSumBytes=%d maxHeapBytes=%d%n",
                WorkOrderUploadProperties.MAX_FILE_BYTES, peak, Runtime.getRuntime().maxMemory());
    }

    private static MultipartFile file(int size) {
        return new MockMultipartFile("file", "attachment.bin", "application/octet-stream", new byte[size]);
    }

    static InputStream zeroStream(long size) {
        return new InputStream() {
            long remaining = size;
            @Override public int read() { return remaining-- > 0 ? 0 : -1; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining <= 0) return -1;
                int count = (int) Math.min(length, remaining);
                remaining -= count;
                return count;
            }
        };
    }
}
