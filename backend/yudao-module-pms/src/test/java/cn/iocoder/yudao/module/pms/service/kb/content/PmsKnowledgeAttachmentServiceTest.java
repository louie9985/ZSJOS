package cn.iocoder.yudao.module.pms.service.kb.content;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeDocumentDO;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.content.PmsKnowledgeAttachmentMapper;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.content.PmsKnowledgeDocumentMapper;
import cn.iocoder.yudao.module.pms.service.kb.library.PmsKnowledgeLibraryMemberService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

@Import(PmsKnowledgeAttachmentService.class)
@Sql("classpath:sql/knowledge_attachment.sql")
class PmsKnowledgeAttachmentServiceTest extends BaseDbUnitTest {
    @Resource PmsKnowledgeAttachmentService service;
    @Resource PmsKnowledgeAttachmentMapper mapper;
    @Resource PmsKnowledgeDocumentMapper documents;
    @MockitoBean FileApi files;
    @MockitoBean PmsKnowledgeContentPermissionService permissions;
    @MockitoBean PmsKnowledgeLibraryMemberService members;
    private PmsKnowledgeDocumentDO document() {
        var doc = randomPojo(PmsKnowledgeDocumentDO.class, d -> d.setId(null).setType(3).setStatus(1));
        documents.insert(doc);
        return doc;
    }
    private void upload(PmsKnowledgeDocumentDO doc, long fileId) {
        var file = new FileInfoRespDTO().setId(fileId).setName("培训.pdf").setSize(12L).setCreator("7")
                .setPath("pms/kb/attachment/" + doc.getId() + "/7/fixture.pdf");
        when(files.getFileInfo(fileId)).thenReturn(file);
        when(files.presignGetUrl(fileId, 600)).thenReturn("https://files.example.test/signed");
    }
    @Test void readablePreviewUserCanDownloadButCannotUpload() {
        var doc = document(); upload(doc, 10); service.replace(doc, List.of(10L), 7L);
        when(permissions.validateContentPermissionReadable(doc.getPermissionId(), doc.getLibraryId(), 8L)).thenReturn(3);
        assertEquals("https://files.example.test/signed", service.url(doc.getId(), 10L, 8L));
        verify(permissions).validateContentPermissionReadable(doc.getPermissionId(), doc.getLibraryId(), 8L);
        when(permissions.validateContentPermissionWritable(doc.getPermissionId(), doc.getLibraryId(), 8L))
                .thenThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(KNOWLEDGE_CONTENT_WRITE_ACCESS_DENIED));
        assertServiceException(() -> service.authorize(doc.getId(), 8L, true), KNOWLEDGE_CONTENT_WRITE_ACCESS_DENIED);
    }
    @Test void selectionRemovalReadditionAndOldClientOmission() throws Exception {
        var doc = document(); upload(doc, 10); upload(doc, 11);
        service.replace(doc, List.of(10L), 7L); service.selectPublic(doc.getId(), List.of(10L));
        service.replace(doc, List.of(10L, 11L), 7L);
        assertEquals(List.of(10L), service.selected(doc.getId()));
        service.replace(doc, null, 7L); assertEquals(2, service.list(doc.getId()).size());
        service.replace(doc, List.of(), 7L);
        assertServiceException(() -> service.boundUrl(doc.getId(), 10L, true), KNOWLEDGE_ATTACHMENT_UNAVAILABLE);
        service.replace(doc, List.of(10L), 7L); assertTrue(service.selected(doc.getId()).isEmpty());
        verify(files, never()).deleteFileIfExists(anyLong());
    }
    @Test void rejectsCrossDocumentFilesAndInvalidSelectionWithoutPartialWrite() {
        var doc = document(); var other = document(); upload(other, 10);
        assertServiceException(() -> service.replace(doc, List.of(10L), 7L), KNOWLEDGE_ATTACHMENT_INVALID);
        assertTrue(service.list(doc.getId()).isEmpty());
        upload(doc, 11); service.replace(doc, List.of(11L), 7L);
        assertServiceException(() -> service.selectPublic(doc.getId(), List.of(11L, 10L)), KNOWLEDGE_ATTACHMENT_INVALID);
        assertTrue(service.selected(doc.getId()).isEmpty());
        assertServiceException(() -> service.replace(doc, List.of(11L,11L), 7L), KNOWLEDGE_ATTACHMENT_INVALID);
    }
    @Test void inaccessibleAndRecycledDocumentsNeverSignFiles() {
        var doc = document(); upload(doc, 10); service.replace(doc, List.of(10L), 7L);
        when(permissions.validateContentPermissionReadable(doc.getPermissionId(), doc.getLibraryId(), 8L))
                .thenThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(KNOWLEDGE_CONTENT_ACCESS_DENIED));
        assertServiceException(() -> service.url(doc.getId(), 10L, 8L), KNOWLEDGE_CONTENT_ACCESS_DENIED);
        documents.updateById(new PmsKnowledgeDocumentDO().setId(doc.getId()).setStatus(-1));
        assertServiceException(() -> service.url(doc.getId(), 10L, 7L), KNOWLEDGE_DOCUMENT_NOT_EXISTS);
        verify(files, never()).presignGetUrl(anyLong(), anyInt());
        documents.updateById(new PmsKnowledgeDocumentDO().setId(doc.getId()).setStatus(1));
        assertEquals(1, service.list(doc.getId()).size());
        service.deleteDocuments(List.of(doc.getId())); assertTrue(service.list(doc.getId()).isEmpty());
    }
}
