package cn.iocoder.yudao.module.pms.service.kb.content;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeDocumentDO;
import cn.iocoder.yudao.module.pms.service.kb.recycle.PmsKnowledgeRecycleService;
import cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeDocumentShareService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.concurrent.Semaphore;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

@Service
public class PmsKnowledgeExcelPreviewService {
    @Resource private PmsKnowledgeAttachmentService attachments;
    @Resource private PmsKnowledgeRecycleService recycle;
    @Resource private PmsKnowledgeDocumentShareService shares;
    @Resource private FileApi files;
    @Resource private PmsKnowledgeExcelParser parser;
    private final Semaphore slots = new Semaphore(2);

    public PmsKnowledgeExcelPreviewRespVO preview(Long documentId, Long fileId, int sheet, Long userId) {
        return read(attachments.authorize(documentId, userId, false), fileId, sheet, false);
    }
    public PmsKnowledgeExcelPreviewRespVO recycle(Long recordId, Long documentId, Long fileId, int sheet, Long userId) {
        return read(recycle.getContentRecyclePreview(recordId, documentId, userId), fileId, sheet, false);
    }
    public PmsKnowledgeExcelPreviewRespVO publicPreview(String token, Long fileId, int sheet) {
        return read(shares.getDocumentByShareToken(token), fileId, sheet, true);
    }
    private PmsKnowledgeExcelPreviewRespVO read(PmsKnowledgeDocumentDO doc, Long fileId, int sheet, boolean shared) {
        FileInfoRespDTO file;
        if (fileId != null) {
            attachments.validateBound(doc.getId(), fileId, shared);
            file = files.getFileInfo(fileId);
        } else {
            if (!Integer.valueOf(4).equals(doc.getType())) throw exception(KNOWLEDGE_EXCEL_UNSUPPORTED);
            file = files.getRegisteredFileByUrl(doc.getContent());
        }
        if (file == null) throw exception(KNOWLEDGE_EXCEL_UNAVAILABLE);
        if (!PmsKnowledgeExcelParser.supports(file.getName(), file.getType())) throw exception(KNOWLEDGE_EXCEL_UNSUPPORTED);
        if (file.getSize() == null || file.getSize() <= 0) throw exception(KNOWLEDGE_EXCEL_UNAVAILABLE);
        if (file.getSize() > PmsKnowledgeExcelParser.MAX_BYTES) throw exception(KNOWLEDGE_EXCEL_TOO_LARGE);
        // Limit both storage reads and parsing, not just rendering of the selected sheet.
        if (!slots.tryAcquire()) throw exception(KNOWLEDGE_EXCEL_BUSY);
        try {
            byte[] content;
            try { content = files.getFileContent(file.getId()); }
            catch (Exception e) { throw exception(KNOWLEDGE_EXCEL_UNAVAILABLE); }
            return parser.parse(content, sheet);
        } finally { slots.release(); }
    }
}
