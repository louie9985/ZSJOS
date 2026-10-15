package cn.iocoder.yudao.module.pms.service.kb.content;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeAttachmentRespVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.*;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.content.*;
import cn.iocoder.yudao.module.pms.service.kb.library.PmsKnowledgeLibraryMemberService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

/** Attachment access deliberately follows document readability, including preview-only readers. */
@Service
public class PmsKnowledgeAttachmentService {
    public static final long MAX_SIZE = 100L * 1024 * 1024;
    public static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip");
    @Resource private PmsKnowledgeAttachmentMapper mapper;
    @Resource private PmsKnowledgeDocumentMapper documents;
    @Resource private PmsKnowledgeContentPermissionService permissions;
    @Resource private PmsKnowledgeLibraryMemberService members;
    @Resource private FileApi files;

    public PmsKnowledgeDocumentDO authorize(Long documentId, Long userId, boolean write) {
        var document = documents.selectById(documentId);
        if (document == null || !Integer.valueOf(1).equals(document.getStatus())) throw exception(KNOWLEDGE_DOCUMENT_NOT_EXISTS);
        members.validateLibraryReadable(document.getLibraryId(), userId);
        if (write) permissions.validateContentPermissionWritable(document.getPermissionId(), document.getLibraryId(), userId);
        else permissions.validateContentPermissionReadable(document.getPermissionId(), document.getLibraryId(), userId);
        return document;
    }

    public PmsKnowledgeAttachmentRespVO upload(Long documentId, MultipartFile file, Long userId) throws Exception {
        var document = authorize(documentId, userId, true);
        if (!Integer.valueOf(3).equals(document.getType()) || file.isEmpty() || file.getSize() > MAX_SIZE || !allowed(file.getOriginalFilename()))
            throw exception(KNOWLEDGE_ATTACHMENT_INVALID);
        var saved = files.createFileInfo(file.getBytes(), file.getOriginalFilename(), directory(documentId, userId), file.getContentType());
        return new PmsKnowledgeAttachmentRespVO().setFileId(saved.getId()).setFileName(saved.getName())
                .setMimeType(saved.getType()).setFileSize(saved.getSize()).setUrl(files.presignGetUrl(saved.getId(), 600));
    }

    static boolean allowed(String name) {
        return name != null && name.contains(".") && EXTENSIONS.contains(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }
    private String directory(Long documentId, Long userId) { return "pms/kb/attachment/" + documentId + "/" + userId; }

    /** Caller owns the document transaction; omitted lists preserve older clients. */
    @Transactional(rollbackFor = Exception.class)
    public void replace(PmsKnowledgeDocumentDO document, List<Long> requested, Long userId) {
        if (requested == null) return;
        if (requested.size() > 10 || requested.stream().anyMatch(Objects::isNull) || new HashSet<>(requested).size() != requested.size()
                || (!requested.isEmpty() && !Integer.valueOf(3).equals(document.getType()))) throw exception(KNOWLEDGE_ATTACHMENT_INVALID);
        lockDocument(document.getId());
        var existing = mapper.list(document.getId());
        Map<Long, PmsKnowledgeAttachmentDO> byFile = new HashMap<>();
        existing.forEach(row -> byFile.put(row.getFileId(), row));
        List<PmsKnowledgeAttachmentDO> next = new ArrayList<>();
        for (Long id : requested) {
            var row = byFile.get(id);
            if (row == null) {
                var file = files.getFileInfo(id);
                if (file == null || !Objects.equals(file.getCreator(), String.valueOf(userId))
                        || file.getPath() == null || !file.getPath().startsWith(directory(document.getId(), userId) + "/")
                        || file.getSize() == null || file.getSize() <= 0 || file.getSize() > MAX_SIZE || !allowed(file.getName()))
                    throw exception(KNOWLEDGE_ATTACHMENT_INVALID);
                row = new PmsKnowledgeAttachmentDO().setDocumentId(document.getId()).setFileId(id)
                        .setFileName(file.getName()).setMimeType(file.getType()).setFileSize(file.getSize()).setShared(false);
            }
            row.setSort(next.size());
            next.add(row);
        }
        // Removed bindings lose their public selection; adding the file again creates an unshared binding.
        existing.stream().filter(row -> !requested.contains(row.getFileId())).forEach(row -> mapper.deleteById(row.getId()));
        next.forEach(row -> { if (row.getId() == null) mapper.insert(row); else mapper.updateById(row); });
    }

    public List<PmsKnowledgeAttachmentRespVO> list(Long documentId) {
        return mapper.list(documentId).stream().map(row -> BeanUtils.toBean(row, PmsKnowledgeAttachmentRespVO.class)).toList();
    }
    public String url(Long documentId, Long fileId, Long userId) {
        authorize(documentId, userId, false);
        return boundUrl(documentId, fileId, false);
    }
    public String boundUrl(Long documentId, Long fileId, boolean sharedOnly) {
        if (mapper.list(documentId).stream().noneMatch(row -> row.getFileId().equals(fileId) && (!sharedOnly || Boolean.TRUE.equals(row.getShared()))))
            throw exception(KNOWLEDGE_ATTACHMENT_UNAVAILABLE);
        if (files.getFileInfo(fileId) == null) throw exception(KNOWLEDGE_ATTACHMENT_UNAVAILABLE);
        return files.presignGetUrl(fileId, 600);
    }
    public List<PmsKnowledgeAttachmentRespVO> publicList(Long documentId, String token) {
        var selected = selected(documentId);
        return list(documentId).stream().filter(row -> selected.contains(row.getFileId()))
                .map(row -> row.setUrl("/public-api/pms/kb/document-share/attachment?token="
                        + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8) + "&fileId=" + row.getFileId())).toList();
    }
    public List<Long> selected(Long documentId) {
        return mapper.list(documentId).stream().filter(row -> Boolean.TRUE.equals(row.getShared())).map(PmsKnowledgeAttachmentDO::getFileId).toList();
    }
    @Transactional(rollbackFor = Exception.class)
    public void selectPublic(Long documentId, List<Long> selected) {
        var ids = selected == null ? List.<Long>of() : selected;
        lockDocument(documentId);
        var rows = mapper.list(documentId);
        if (ids.size() > 10 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()
                || !rows.stream().map(PmsKnowledgeAttachmentDO::getFileId).toList().containsAll(ids)) throw exception(KNOWLEDGE_ATTACHMENT_INVALID);
        rows.forEach(row -> mapper.updateById(new PmsKnowledgeAttachmentDO().setId(row.getId()).setShared(ids.contains(row.getFileId()))));
    }
    private void lockDocument(Long id) {
        documents.selectOne(new LambdaQueryWrapperX<PmsKnowledgeDocumentDO>().eq(PmsKnowledgeDocumentDO::getId, id).last("FOR UPDATE"));
    }
    public void deleteDocuments(Collection<Long> ids) {
        if (!ids.isEmpty()) mapper.delete(new LambdaQueryWrapperX<PmsKnowledgeAttachmentDO>().in(PmsKnowledgeAttachmentDO::getDocumentId, ids));
    }
}
