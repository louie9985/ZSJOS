package cn.iocoder.yudao.module.pms.controller.admin.kb.interaction;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
import cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeExcelPreviewService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/** Compatibility JSON adapter for the existing anonymous Vue share page. */
@RestController
@RequestMapping("/pms/kb/document-share")
public class PmsKnowledgeExcelShareController {
    @Resource private PmsKnowledgeExcelPreviewService excel;
    @GetMapping("/excel-preview")
    @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<PmsKnowledgeExcelPreviewRespVO> preview(@RequestParam String token,
            @RequestParam(required = false) Long fileId, @RequestParam(defaultValue = "-1") int sheetIndex,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return success(excel.publicPreview(token, fileId, sheetIndex));
    }
}
