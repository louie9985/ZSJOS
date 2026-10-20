package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFormService;
import cn.idev.excel.annotation.ExcelProperty;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/registration")
public class StudentInfoFormController {
    @Resource private StudentInfoFormService service;
    @ModelAttribute
    public void disableCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @PostMapping("/{caseId}/student-info-form")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:create')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Link> generate(@PathVariable Long caseId,@Valid @RequestBody Command request) { return success(service.generate(caseId,request)); }

    @GetMapping("/{caseId}/student-info-form/link")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:link-read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Link> link(@PathVariable Long caseId) { return success(service.getLink(caseId)); }

    @PostMapping("/{caseId}/student-info-form/regenerate")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:regenerate')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Link> regenerate(@PathVariable Long caseId,@Valid @RequestBody Command request) {
        return success(service.regenerate(caseId,request));
    }
    @PostMapping("/{caseId}/student-info-form/revoke")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:revoke')")
    public CommonResult<Link> revoke(@PathVariable Long caseId,@Valid @RequestBody Command request) {
        return success(service.revoke(caseId,request));
    }
    @PostMapping("/{caseId}/student-info-form/sent")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:sent')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Link> sent(@PathVariable Long caseId,@Valid @RequestBody Command request) {
        return success(service.confirmSent(caseId,request));
    }
    @Resource private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService images;
    @GetMapping("/{caseId}/student-info-form/images/{imageId}")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public byte[] image(@PathVariable Long caseId,@PathVariable Long imageId,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setContentType("application/octet-stream");
        return images.internalRead(caseId,imageId);
    }
    @GetMapping("/{caseId}/student-info-form/detail")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Detail> detail(@PathVariable Long caseId) { return success(service.detail(caseId)); }

    @GetMapping("/{caseId}/student-info-form/sensitive")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @studentInfoFormService.canReadSensitive()")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Detail> sensitive(@PathVariable Long caseId) { return success(service.sensitiveDetail(caseId)); }

    @GetMapping("/{caseId}/student-info-form/export")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:export')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public void export(@PathVariable Long caseId,HttpServletResponse response) throws IOException {
        Detail detail=service.exportDetail(caseId);
        var rows=detail.getFields().stream().map(f -> {
            ExportRow row=new ExportRow(); row.setField(f.getLabel());
            row.setValue("images".equals(f.getType()) ? detail.getImages().stream().filter(i->f.getKey().equals(i.getFieldKey())).map(i->i.getName()).collect(java.util.stream.Collectors.joining("；")) : detail.getValues().getOrDefault(f.getKey(),"")); return row;
        }).toList();
        ExcelUtils.write(response,"学员信息.xlsx","学员信息",ExportRow.class,rows);
    }
    @Data public static class ExportRow {
        @ExcelProperty("字段") private String field;
        @ExcelProperty("内容") private String value;
    }
}
