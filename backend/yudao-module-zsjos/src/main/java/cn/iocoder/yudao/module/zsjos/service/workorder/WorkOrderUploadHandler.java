package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderFileRespVO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderUploadErrorCodes.FILE_SIZE_INVALID;
import static cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderUploadErrorCodes.UPLOAD_BUSY;

/** Bounds allocations before reading multipart files; the reservation includes the storage call. */
@Component
public class WorkOrderUploadHandler {
    private final WorkOrderService service;
    private final int maxConcurrentRequests;
    private final long maxInFlightBytes;
    private int activeRequests;
    private long reservedBytes;

    public WorkOrderUploadHandler(WorkOrderService service, WorkOrderUploadProperties properties) {
        this.service = service;
        this.maxConcurrentRequests = properties.getMaxConcurrentRequests();
        this.maxInFlightBytes = properties.getMaxInFlightBytes();
        if (maxConcurrentRequests < 1 || maxConcurrentRequests > 2
                || maxInFlightBytes < 1 || maxInFlightBytes > WorkOrderUploadProperties.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid work-order upload budget");
        }
    }

    public WorkOrderFileRespVO upload(MultipartFile file, Long userId) throws IOException {
        long size = file.getSize();
        validateSize(size);
        reserve(size);
        // Release on open/read/close failure, storage failure and errors including allocation failure.
        try (InputStream input = file.getInputStream()) {
            byte[] content = new byte[(int) size];
            int offset = 0;
            while (offset < content.length) {
                int count = input.read(content, offset, content.length - offset);
                if (count < 0) throw exception(WORK_ORDER_ATTACHMENT_INVALID);
                if (count == 0) {
                    int value = input.read();
                    if (value < 0) throw exception(WORK_ORDER_ATTACHMENT_INVALID);
                    content[offset++] = (byte) value;
                } else {
                    offset += count;
                }
            }
            if (input.read() != -1) throw exception(WORK_ORDER_ATTACHMENT_INVALID);
            return service.upload(content, file.getOriginalFilename(), file.getContentType(), userId);
        } finally {
            release(size);
        }
    }

    public static void validateSize(long size) {
        if (size <= 0 || size > WorkOrderUploadProperties.MAX_FILE_BYTES) {
            throw exception(FILE_SIZE_INVALID);
        }
    }

    private synchronized void reserve(long size) {
        if (activeRequests >= maxConcurrentRequests || size > maxInFlightBytes - reservedBytes) {
            throw exception(UPLOAD_BUSY);
        }
        activeRequests++;
        reservedBytes += size;
    }

    private synchronized void release(long size) {
        activeRequests--;
        reservedBytes -= size;
    }
}
