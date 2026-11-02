package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoImageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentInfoTaskAttachmentTest {
    @Test void copyOwnsFileAndBindingWithoutTouchingHistory() throws Exception { copy(false); }
    @Test void rollbackCompensatesOnlyTheNewFile() throws Exception { copy(true); }
    private void copy(boolean rollback) throws Exception {
        var service=new StudentInfoImageService();var mapper=mock(StudentInfoImageMapper.class);var files=mock(FileApi.class);
        ReflectionTestUtils.setField(service,"images",mapper);ReflectionTestUtils.setField(service,"files",files);
        var source=new StudentInfoFormDO();source.setId(1L);var target=new StudentInfoFormDO();target.setId(2L);target.setTenantId(1L);
        var original=new StudentInfoImageDO();original.setId(3L);original.setFormId(1L);original.setInfraFileId(4L);
        original.setFieldKey("portrait");original.setSide("");original.setFileSize(8L);original.setContentType("image/png");
        when(mapper.selectById(3L)).thenReturn(original);when(files.getFileContent(4L)).thenReturn(new byte[]{1,2,3});
        var stored=new FileInfoRespDTO();stored.setId(5L);when(files.createFileInfo(any(byte[].class),anyString(),eq("student-info-private/2"),eq("application/octet-stream"))).thenReturn(stored);
        var field=new Field();field.setKey("portrait");field.setType("images");field.setMaxImages(9);field.setMaxSizeMb(100);field.setAllowedExtensions(List.of("png"));
        var image=new Image();image.setId(3L);image.setFieldKey("portrait");image.setContentType("image/png");image.setSize(8L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.copyForCollection(source,target,List.of(image),List.of(field));
            var captured=ArgumentCaptor.forClass(StudentInfoImageDO.class);verify(mapper).insert(captured.capture());
            assertEquals(2L,captured.getValue().getFormId());assertEquals(5L,captured.getValue().getInfraFileId());
            assertEquals(1L,original.getFormId());assertEquals(4L,original.getInfraFileId());
            for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(rollback ? TransactionSynchronization.STATUS_ROLLED_BACK : TransactionSynchronization.STATUS_COMMITTED);
            if(rollback) verify(files).deleteFileIfExists(5L); else verify(files,never()).deleteFileIfExists(anyLong());
            verify(files,never()).deleteFileIfExists(4L);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
