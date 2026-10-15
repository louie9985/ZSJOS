package cn.iocoder.yudao.module.zsjos.controller.admin.workorder;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderService;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderUploadHandler;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderUploadProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WorkOrderUploadBoundaryTest {
    @Test
    void oversizedFileMustBeRejectedBeforeOpeningItsStream() throws Exception {
        WorkOrderController controller = new WorkOrderController();
        WorkOrderService service = mock(WorkOrderService.class);
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "uploadHandler", new WorkOrderUploadHandler(service, new WorkOrderUploadProperties()));
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        when(file.getInputStream()).thenThrow(new AssertionError("oversized stream opened"));

        assertThrows(ServiceException.class, () -> controller.upload(file));

        verify(file, never()).getInputStream();
        verifyNoInteractions(service);
    }
}
