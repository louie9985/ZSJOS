package cn.iocoder.yudao.module.pms.service.kb.content;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeDocumentDO;
import cn.iocoder.yudao.module.pms.service.kb.recycle.PmsKnowledgeRecycleService;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeDocumentShareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

class PmsKnowledgeExcelPreviewServiceTest {
    @InjectMocks PmsKnowledgeExcelPreviewService service;
    @Mock PmsKnowledgeAttachmentService attachments;
    @Mock PmsKnowledgeRecycleService recycle;
    @Mock PmsKnowledgeDocumentShareService shares;
    @Mock FileApi files;
    @Mock PmsKnowledgeExcelParser parser;
    PmsKnowledgeDocumentDO doc;
    AutoCloseable mocks;
    @BeforeEach void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        doc=new PmsKnowledgeDocumentDO().setId(1L).setType(4).setContent("https://files.test/excel");
    }
    @Test void deniedBeforeStorageAndForgedBinding() {
        when(attachments.authorize(1L,7L,false)).thenThrow(exception(KNOWLEDGE_CONTENT_ACCESS_DENIED));
        assertServiceException(() -> service.preview(1L,null,-1,7L),KNOWLEDGE_CONTENT_ACCESS_DENIED);
        verifyNoInteractions(files,parser);
        doReturn(doc).when(attachments).authorize(1L,7L,false);
        doThrow(exception(KNOWLEDGE_ATTACHMENT_UNAVAILABLE)).when(attachments).validateBound(1L,99L,false);
        assertServiceException(() -> service.preview(1L,99L,-1,7L),KNOWLEDGE_ATTACHMENT_UNAVAILABLE);
        verifyNoInteractions(files,parser);
    }
    @AfterEach void close() throws Exception { mocks.close(); }
    @Test void concurrentRequestsAreBoundedAndSlotsReleased() throws Exception {
        when(attachments.authorize(1L,7L,false)).thenReturn(doc);
        when(files.getFileInfo(9L)).thenReturn(new FileInfoRespDTO().setId(9L).setName("培训.xlsx").setSize(100L));
        var entered = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        when(files.getFileContent(9L)).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException();
            return new byte[]{1};
        });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> service.preview(1L,9L,-1,7L));
            var second=executor.submit(() -> service.preview(1L,9L,-1,7L));
            try {
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                assertServiceException(() -> service.preview(1L,9L,-1,7L),KNOWLEDGE_EXCEL_BUSY);
            } finally { release.countDown(); }
            first.get(); second.get();
        }
        when(files.getFileContent(9L)).thenThrow(new java.io.IOException("fixture"));
        assertServiceException(() -> service.preview(1L,9L,-1,7L),KNOWLEDGE_EXCEL_UNAVAILABLE);
        doReturn(new byte[]{1}).when(files).getFileContent(9L);
        assertDoesNotThrow(() -> service.preview(1L,9L,-1,7L));
    }
    @Test void readableAndRecycleUseBoundFileAndOriginalStore() throws Exception {
        when(attachments.authorize(1L,7L,false)).thenReturn(doc);
        when(files.getFileInfo(9L)).thenReturn(new FileInfoRespDTO().setId(9L).setName("培训.xlsx").setSize(100L));
        when(files.getFileContent(9L)).thenReturn(new byte[]{1});
        service.preview(1L,9L,1,7L);
        verify(attachments).validateBound(1L,9L,false);
        verify(parser).parse(new byte[]{1},1);
        when(recycle.getContentRecyclePreview(8L,1L,7L)).thenReturn(doc);
        service.recycle(8L,1L,9L,-1,7L);
        verify(recycle).getContentRecyclePreview(8L,1L,7L);
    }
    @Test void publicRevalidatesTokenAndSelectedAttachment() {
        when(shares.getDocumentByShareToken("fixture")).thenReturn(doc);
        doThrow(exception(KNOWLEDGE_ATTACHMENT_UNAVAILABLE)).when(attachments).validateBound(1L,9L,true);
        assertServiceException(() -> service.publicPreview("fixture",9L,-1), KNOWLEDGE_ATTACHMENT_UNAVAILABLE);
        when(shares.getDocumentByShareToken("fixture")).thenThrow(exception(KNOWLEDGE_DOCUMENT_SHARE_INVALID));
        assertServiceException(() -> service.publicPreview("fixture",null,-1), KNOWLEDGE_DOCUMENT_SHARE_INVALID);
        verifyNoInteractions(files,parser);
    }
    @Test void independentFileOnlyUsesRegisteredAddressAndChecksSize() throws Exception {
        when(attachments.authorize(1L,7L,false)).thenReturn(doc);
        assertServiceException(() -> service.preview(1L,null,-1,7L),KNOWLEDGE_EXCEL_UNAVAILABLE);
        when(files.getRegisteredFileByUrl(doc.getContent())).thenReturn(new FileInfoRespDTO().setId(9L).setName("培训.xlsx").setSize(11L*1024*1024));
        assertServiceException(() -> service.preview(1L,null,-1,7L),KNOWLEDGE_EXCEL_TOO_LARGE);
        verify(files,never()).getFileContent(anyLong());
    }
}
