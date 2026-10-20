package cn.iocoder.yudao.module.pms.controller.admin.kb.content;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
import cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeExcelPreviewService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/pms/kb/document/excel")
public class PmsKnowledgeExcelPreviewController {
    @Resource private PmsKnowledgeExcelPreviewService excel;
    @GetMapping("/preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('pms:kb:library:query')")
    public CommonResult<PmsKnowledgeExcelPreviewRespVO> preview(@RequestParam Long documentId,
            @RequestParam(required = false) Long fileId, @RequestParam(defaultValue = "-1") int sheetIndex,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(excel.preview(documentId, fileId, sheetIndex, getLoginUserId()));
    }
    @GetMapping("/recycle-preview")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @PreAuthorize("@ss.hasPermission('pms:kb:library:delete')")
    public CommonResult<PmsKnowledgeExcelPreviewRespVO> recycle(@RequestParam Long recordId,
            @RequestParam Long documentId, @RequestParam(required = false) Long fileId,
            @RequestParam(defaultValue = "-1") int sheetIndex, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(excel.recycle(recordId, documentId, fileId, sheetIndex, getLoginUserId()));
    }
}
