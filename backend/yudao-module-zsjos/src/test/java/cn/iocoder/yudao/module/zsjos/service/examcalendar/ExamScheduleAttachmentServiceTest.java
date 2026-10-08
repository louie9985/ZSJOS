package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.ExamScheduleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.ExamScheduleMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamScheduleAttachmentServiceTest {
    @Mock FileApi fileApi;
    @Mock PermissionApi permissionApi;
    @Mock ExamScheduleMapper mapper;
    @Mock ExamScheduleObjectPermissionProvider objectPermissions;
    @InjectMocks ExamScheduleAttachmentService service;
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    void manage() { when(permissionApi.hasAnyPermissions(7L, ExamScheduleService.PERMISSION_MANAGE)).thenReturn(true); }
    FileInfoRespDTO file(String path, String creator) {
        return new FileInfoRespDTO(11L, 1L, "官方通知.pdf", path, "https://example.test/file", "application/pdf", 20L, creator);
    }
    @Test void permissionDeniedBeforeStorage() {
        assertThrows(ServiceException.class, () -> service.upload(new MockMultipartFile("file", new byte[]{1}), 7L));
        assertThrows(ServiceException.class, () -> service.validate(List.of(11L), null, 7L));
        verifyNoInteractions(fileApi, mapper);
    }
    @Test void realFileTypeAndSizeAreValidated() throws Exception {
        manage();
        var info = file("zsjos/exam-attachment/1/7/a.pdf", "7");
        when(fileApi.createFileInfo(any(), eq("官方通知.pdf"), eq("zsjos/exam-attachment/1/7"), eq("application/pdf"))).thenReturn(info);
        when(fileApi.presignGetUrl(11L, 3600)).thenReturn("https://example.test/signed");
        assertEquals(11L, service.upload(new MockMultipartFile("file", "官方通知.pdf", "text/plain", "%PDF-1.7\nnotice".getBytes()), 7L).getFileId());
        assertThrows(ServiceException.class, () -> service.upload(new MockMultipartFile("file", "bad.png", "image/png", "<html>unsafe</html>".getBytes()), 7L));
        assertThrows(ServiceException.class, () -> service.upload(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]), 7L));
        assertThrows(ServiceException.class, () -> service.upload(new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[(int) ExamScheduleAttachmentService.MAX_SIZE + 1]), 7L));
        verify(fileApi, times(1)).createFileInfo(any(), any(), any(), any());
    }
    @Test void bindingAllows100MbAndRejectsOneByteOver() {
        manage();
        var info = file("zsjos/exam-attachment/1/7/a.pdf", "7");
        info.setSize(100L * 1024 * 1024);
        when(fileApi.getFileInfo(11L)).thenReturn(info);
        assertEquals("[11]", service.validate(List.of(11L), null, 7L));
        info.setSize(100L * 1024 * 1024 + 1);
        assertThrows(ServiceException.class, () -> service.validate(List.of(11L), null, 7L));
    }
    @Test void currentBindingAndOwnUploadsAreAllowedButOtherPendingUploadsAreNot() {
        manage(); when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/exam-attachment/1/7/a.pdf", "7"));
        assertEquals("[11]", service.validate(List.of(11L), null, 7L));
        when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/exam-attachment/1/8/a.pdf", "8"));
        assertThrows(ServiceException.class, () -> service.validate(List.of(11L), null, 7L));
        assertEquals("[11]", service.validate(List.of(11L), "[11]", 7L));
        when(mapper.countClaimedAttachment(11L, 1L, 7L)).thenReturn(1L);
        assertEquals("[11]", service.validate(List.of(11L), null, 7L));
    }
    @Test void tenantNamespaceAndOtherBusinessFilesCannotBeForgedEvenIfBound() {
        manage();
        for (String path : List.of("zsjos/exam-attachment/2/7/a.pdf", "zsjos/exam-attachment/10/7/a.pdf", "zsjos/lead/admin/a.pdf")) {
            when(fileApi.getFileInfo(11L)).thenReturn(file(path, "7"));
            assertThrows(ServiceException.class, () -> service.validate(List.of(11L), "[11]", 7L));
        }
    }
    @Test void omittedPreservesEmptyClearsAndDuplicatesFail() {
        manage(); assertEquals("[11]", service.validate(null, "[11]", 7L));
        assertEquals("[]", service.validate(List.of(), "[11]", 7L));
        assertThrows(ServiceException.class, () -> service.validate(List.of(11L, 11L), null, 7L));
        assertThrows(ServiceException.class, () -> service.validate(java.util.stream.LongStream.range(1, 12).boxed().toList(), null, 7L));
        verifyNoInteractions(fileApi);
    }
    @Test void readRequiresVisibleScheduleAndBoundFileBeforeSigning() {
        when(mapper.selectById(5L)).thenReturn(new ExamScheduleDO().setAttachmentIdsJson("[11]"));
        when(fileApi.getFileInfo(11L)).thenReturn(file("zsjos/exam-attachment/1/7/a.pdf", "7"));
        when(fileApi.presignGetUrl(11L, 3600)).thenReturn("https://example.test/new");
        assertEquals("https://example.test/new", service.read(5L, 11L, 7L).getUrl());
        assertThrows(ServiceException.class, () -> service.read(5L, 12L, 7L));
        verify(objectPermissions, times(2)).check(5L, "read-attachment", 7L);
        verify(fileApi, never()).getFileInfo(12L);
    }
    @Test void unavailableFilesRemainRemovable() {
        var result = service.describe("[11]");
        assertEquals(11L, result.getFirst().getFileId()); assertNull(result.getFirst().getUrl());
        verify(fileApi, never()).presignGetUrl(anyLong(), anyInt());
    }
}
