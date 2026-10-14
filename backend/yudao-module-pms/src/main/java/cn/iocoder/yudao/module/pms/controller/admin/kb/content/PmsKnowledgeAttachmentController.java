package cn.iocoder.yudao.module.pms.controller.admin.kb.content;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeAttachmentRespVO;
import cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeAttachmentService;
import cn.iocoder.yudao.module.pms.service.kb.recycle.PmsKnowledgeRecycleService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/pms/kb/document/attachment")
public class PmsKnowledgeAttachmentController {
    @Resource private PmsKnowledgeAttachmentService attachments;
    @Resource private PmsKnowledgeRecycleService recycle;
    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:update')")
    public CommonResult<PmsKnowledgeAttachmentRespVO> upload(@RequestParam Long documentId, @RequestParam MultipartFile file) throws Exception {
        return success(attachments.upload(documentId, file, getLoginUserId()));
    }
    @GetMapping("/url")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<String> url(@RequestParam Long documentId, @RequestParam Long fileId) {
        return success(attachments.url(documentId, fileId, getLoginUserId()));
    }
    @GetMapping("/recycle-url")
    @PreAuthorize("@ss.hasPermission('pms:kb:library:delete')")
    public CommonResult<String> recycleUrl(@RequestParam Long recordId, @RequestParam Long documentId, @RequestParam Long fileId) {
        var document = recycle.getContentRecyclePreview(recordId, documentId, getLoginUserId());
        return success(attachments.boundUrl(document.getId(), fileId, false));
    }
}
