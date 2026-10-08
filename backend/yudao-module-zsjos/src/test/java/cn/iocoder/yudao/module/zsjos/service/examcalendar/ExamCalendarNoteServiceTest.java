package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamCalendarNoteSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcalendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcalendar.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExamCalendarNoteServiceTest {
    @BeforeAll static void metadata() {
        for (Class<?> type : List.of(ExamCalendarNoteDO.class, ExamCalendarNoteImageDO.class)) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "note-test"), type);
        }
    }
    @Mock ExamCalendarNoteMapper notes;
    @Mock ExamCalendarNoteImageMapper images;
    @Mock FileApi files;
    @Mock PermissionApi permissions;
    ExamCalendarNotePermissionProvider access = new ExamCalendarNotePermissionProvider();
    @InjectMocks ExamCalendarNoteService service;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(access, "permissions", permissions);
        ReflectionTestUtils.setField(service, "access", access);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    ExamCalendarNoteDO row() {
        var row = new ExamCalendarNoteDO().setId(9L).setContent("旧内容").setVersion(1L);
        row.setTenantId(1L); row.setDeleted(false); return row;
    }
    ExamCalendarNoteSaveReqVO req(String html, long version) { return new ExamCalendarNoteSaveReqVO().setContent(html).setVersion(version); }
    void allowWrite() { when(permissions.hasAnyPermissions(7L, "zsjos:exam-calendar:manage")).thenReturn(true); }
    @Test void deniesReadWriteUploadBeforePersistenceAndRejectsForeignTenant() throws Exception {
        assertThrows(ServiceException.class, () -> service.get(1L,7L));
        assertThrows(ServiceException.class, () -> service.save(1L,7L,req("text",0)));
        assertThrows(ServiceException.class, () -> service.upload(1L,7L,new MockMultipartFile("file", new byte[]{1})));
        assertThrows(ServiceException.class, () -> service.get(2L,7L));
        verifyNoInteractions(notes,images,files);
    }
    @Test void queryOnlyReturnsEmptyWithoutCreatingRow() {
        when(permissions.hasAnyPermissions(7L,"zsjos:exam-calendar:query")).thenReturn(true);
        var result = service.get(1L,7L);
        assertEquals(0L,result.getVersion()); assertEquals("",result.getContent());
        assertThrows(ServiceException.class, () -> service.save(1L,7L,req("text",0)));
        verify(notes,never()).ensureRow(anyLong(),anyLong());
    }
    @Test void versionConflictDoesNotChangeContentOrBindings() {
        allowWrite(); when(notes.lock(1L)).thenReturn(row());
        assertEquals(1900018021,assertThrows(ServiceException.class, () -> service.save(1L,7L,req("new",0))).getCode());
        verify(notes,never()).updateById(any(ExamCalendarNoteDO.class)); verifyNoInteractions(images,files);
    }
    @Test void persistsCleanedContentAndAllowsClear() {
        allowWrite(); when(notes.lock(1L)).thenReturn(row());
        assertEquals("<p><b>新内容</b></p>",service.save(1L,7L,req("<p onclick='x'><b>新内容</b></p>",1)).getContent());
        assertEquals("",service.save(1L,7L,req("<p><br></p>",2)).getContent());
        verify(notes,times(2)).updateById(any(ExamCalendarNoteDO.class));
    }
    @Test void rejectsAnotherUsersUnboundImageAndUnknownImage() {
        allowWrite(); when(notes.lock(1L)).thenReturn(row());
        var binding = new ExamCalendarNoteImageDO().setFileId(8L).setUploadedBy(6L).setBound(false); binding.setTenantId(1L);
        when(images.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        assertEquals(1900018023,assertThrows(ServiceException.class, () -> service.save(1L,7L,req("<img src='exam-note-image:8'>",1))).getCode());
        when(images.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThrows(ServiceException.class, () -> service.save(1L,7L,req("<img src='exam-note-image:8'>",1)));
        verify(notes,never()).updateById(any(ExamCalendarNoteDO.class));
    }
    @Test void acceptsOwnPendingOrExistingSharedImageButStoresNoSignedUrl() {
        allowWrite(); when(notes.lock(1L)).thenReturn(row());
        var binding = new ExamCalendarNoteImageDO().setFileId(8L).setUploadedBy(7L).setBound(false); binding.setTenantId(1L);
        when(images.selectList(any(Wrapper.class))).thenReturn(List.of(binding));
        when(files.presignGetUrl(8L,3600)).thenReturn("https://example.com/image?signature=current");
        var response = service.save(1L,7L,req("<img src='exam-note-image:8'>",1));
        assertEquals("<img src=\"exam-note-image:8\">",response.getContent());
        assertTrue(response.getImages().getFirst().getUrl().contains("signature"));
        binding.setUploadedBy(6L).setBound(true);
        assertEquals(3L,service.save(1L,7L,req(response.getContent(),2)).getVersion());
    }
}
