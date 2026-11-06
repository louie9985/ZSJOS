package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ExamCollectionAttachmentTest {
    ExamCollectionAttachmentService service;
    ExamCollectionAttachmentMapper attachments;
    FileApi files;
    ExamCollectionPublicService sessions;
    ExamCollectionTaskDO task;
    ExamCollectionSubmissionDO session;
    MockMultipartFile file;
    @BeforeEach void setup()throws Exception {
        service=new ExamCollectionAttachmentService();attachments=mock(ExamCollectionAttachmentMapper.class);files=mock(FileApi.class);sessions=mock(ExamCollectionPublicService.class);
        ReflectionTestUtils.setField(service,"attachments",attachments);ReflectionTestUtils.setField(service,"files",files);ReflectionTestUtils.setField(service,"service",sessions);
        var cipher=new StudentInfoImageService();ReflectionTestUtils.setField(cipher,"key","synthetic-exam-test-key");ReflectionTestUtils.setField(service,"cipher",cipher);
        task=new ExamCollectionTaskDO();task.setId(1L);task.setTenantId(1L);session=new ExamCollectionSubmissionDO();session.setId(2L);
        doAnswer(call->((BiFunction<ExamCollectionTaskDO,ExamCollectionSubmissionDO,Object>)call.getArgument(3)).apply(task,session))
                .when(sessions).withSession(anyString(),anyString(),eq(true),any());
        when(sessions.definitions(task)).thenReturn(new StudentInfoFields().presets());
        var stored=new FileInfoRespDTO();stored.setId(100L);stored.setConfigId(7L);stored.setPath("exam-collection-private/1/2/synthetic.bin");when(files.createFileInfo(any(byte[].class),anyString(),anyString(),anyString())).thenReturn(stored);
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        file=new MockMultipartFile("file","identity.png","image/png",bytes.toByteArray());
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void clear(){TransactionSynchronizationManager.clearSynchronization();}
    @Test void responseLossRetryReturnsSameAttachmentEvenWhenBothIdentitySlotsAreFull() throws Exception {
        var saved=new AtomicReference<ExamCollectionAttachmentDO>();
        when(attachments.selectOne(any(LambdaQueryWrapperX.class))).thenAnswer(call->saved.get());
        doAnswer(call->{var row=(ExamCollectionAttachmentDO)call.getArgument(0);row.setId(7L);saved.set(row);return 1;}).when(attachments).insert(any(ExamCollectionAttachmentDO.class));
        assertEquals(7L,service.upload("token","session","identity_images","front",file,"request-one").getId());
        var back=new ExamCollectionAttachmentDO();back.setSide("back");when(attachments.selectList(any(LambdaQueryWrapperX.class))).thenReturn(List.of(saved.get(),back));
        assertEquals(7L,service.upload("token","session","identity_images","front",file,"request-one").getId());
        var encrypted=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(files,times(1)).createFileInfo(encrypted.capture(),anyString(),eq("exam-collection-private/1/2"),eq("application/octet-stream"));
        assertFalse(Arrays.equals(file.getBytes(),encrypted.getValue()));
        verify(attachments,times(1)).insert(any(ExamCollectionAttachmentDO.class));
        assertThrows(ServiceException.class,()->service.upload("token","session","identity_images","back",file,"request-one"));
        assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());
    }
    @Test void failedBusinessBindingCompensatesOnlyItsNewEncryptedObject()throws Exception {
        doThrow(new IllegalStateException("synthetic persistence failure")).when(attachments).insert(any(ExamCollectionAttachmentDO.class));
        assertThrows(ServiceException.class,()->service.upload("token","session","portrait","",file,"request-two"));
        for(var callback:TransactionSynchronizationManager.getSynchronizations())callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(files).deleteUnregisteredFile(7L,"exam-collection-private/1/2/synthetic.bin");
    }
    @Test void missingRequestKeyAndUnselectedFieldNeverStoreFile() {
        assertThrows(ServiceException.class,()->service.upload("token","session","portrait","",file,null));
        assertThrows(ServiceException.class,()->service.upload("token","session","gift_shipping_address","",file,"request-three"));
        verifyNoInteractions(files);
    }
    @Test void replayCannotReturnAnAttachmentFromAnotherTenant() {
        var foreign=new ExamCollectionAttachmentDO();foreign.setTenantId(2L);foreign.setTaskId(1L);foreign.setSubmissionId(2L);
        when(attachments.selectOne(any(LambdaQueryWrapperX.class))).thenReturn(foreign);
        var error=assertThrows(ServiceException.class,()->service.upload("token","session","portrait","",file,"request-foreign"));
        assertEquals(ExamCollectionConstants.SESSION_INVALID.getCode(),error.getCode());verifyNoInteractions(files);
    }
}
