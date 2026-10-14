package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.WorkOrderFileRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Standalone -Xmx192m probe: lazy input and a non-retaining storage double avoid fixture copies. */
public final class WorkOrderUploadMemoryProbe {
    public static void main(String[] args) throws Exception {
        CountDownLatch bothStored = new CountDownLatch(2);
        CountDownLatch finishStorage = new CountDownLatch(1);
        AtomicBoolean block = new AtomicBoolean();
        WorkOrderService storage = (WorkOrderService) Proxy.newProxyInstance(WorkOrderService.class.getClassLoader(),
                new Class<?>[]{WorkOrderService.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("upload")) throw new AssertionError("unexpected service call");
                    if (block.get()) {
                        bothStored.countDown();
                        if (!finishStorage.await(10, TimeUnit.SECONDS)) throw new AssertionError("probe timeout");
                    }
                    return new WorkOrderFileRespVO();
                });
        WorkOrderUploadHandler handler = new WorkOrderUploadHandler(storage, new WorkOrderUploadProperties());
        long limit = WorkOrderUploadProperties.MAX_FILE_BYTES;
        for (int i = 0; i < 8; i++) handler.upload(file(limit), 1L);
        try {
            handler.upload(file(limit + 1), 1L);
            throw new AssertionError("oversize accepted");
        } catch (ServiceException expected) {
            if (!expected.getCode().equals(WorkOrderUploadErrorCodes.FILE_SIZE_INVALID.getCode())) throw expected;
        }
        block.set(true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> handler.upload(file(limit / 2), 1L));
            var second = executor.submit(() -> handler.upload(file(limit / 2), 1L));
            try {
                if (!bothStored.await(10, TimeUnit.SECONDS)) throw new AssertionError("two uploads not admitted");
                try {
                    handler.upload(file(1), 1L);
                    throw new AssertionError("third upload accepted");
                } catch (ServiceException expected) {
                    if (!expected.getCode().equals(WorkOrderUploadErrorCodes.UPLOAD_BUSY.getCode())) throw expected;
                }
            } finally {
                block.set(false);
                finishStorage.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        handler.upload(file(limit), 1L);
        System.out.println("PASS 8x100MB, 100MB+1 rejection, 2x50MB with third denied, and subsequent 100MB; maxHeap="
                + Runtime.getRuntime().maxMemory());
    }

    private static MultipartFile file(long size) {
        return (MultipartFile) Proxy.newProxyInstance(MultipartFile.class.getClassLoader(), new Class<?>[]{MultipartFile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSize" -> size;
                    case "getInputStream" -> {
                        if (size > WorkOrderUploadProperties.MAX_FILE_BYTES) throw new AssertionError("oversize stream opened");
                        yield WorkOrderUploadHandlerTest.zeroStream(size);
                    }
                    case "getOriginalFilename" -> "probe.bin";
                    case "getContentType" -> "application/octet-stream";
                    default -> throw new AssertionError("unexpected multipart call");
                });
    }
}
