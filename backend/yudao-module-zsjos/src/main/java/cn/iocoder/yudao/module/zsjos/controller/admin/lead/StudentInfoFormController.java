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
@RequestMapping("/zsjos/lead")
public class StudentInfoFormController {
    @Resource private StudentInfoFormService service;
    @ModelAttribute
    public void disableCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @PostMapping("/{leadId}/student-info-form")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:create')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Link> generate(@PathVariable Long leadId) { return success(service.generate(leadId)); }

    @GetMapping("/{leadId}/student-info-form/link")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:link-read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Link> link(@PathVariable Long leadId) { return success(service.getLink(leadId)); }

    @PostMapping("/{leadId}/student-info-form/regenerate")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:regenerate')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Link> regenerate(@PathVariable Long leadId,@Valid @RequestBody Command request) {
        return success(service.regenerate(leadId,request.getFormId()));
    }
    @PostMapping("/{leadId}/student-info-form/revoke")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:revoke')")
    public CommonResult<Boolean> revoke(@PathVariable Long leadId,@Valid @RequestBody Command request) {
        service.revoke(leadId,request.getFormId()); return success(true);
    }
    @GetMapping("/{leadId}/student-info-form/detail")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Detail> detail(@PathVariable Long leadId) { return success(service.detail(leadId)); }

    @GetMapping("/{leadId}/student-info-form/sensitive")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:sensitive-read')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Detail> sensitive(@PathVariable Long leadId) { return success(service.sensitiveDetail(leadId)); }

    @GetMapping("/{leadId}/student-info-form/export")
    @PreAuthorize("@ss.hasPermission('zsjos:student-info-form:read') && @ss.hasPermission('zsjos:student-info-form:export')")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public void export(@PathVariable Long leadId,HttpServletResponse response) throws IOException {
        Detail detail=service.exportDetail(leadId);
        var rows=detail.getFields().stream().map(f -> {
            ExportRow row=new ExportRow(); row.setField(f.getLabel());
            row.setValue(detail.getValues().getOrDefault(f.getKey(),"")); return row;
        }).toList();
        ExcelUtils.write(response,"学员信息.xlsx","学员信息",ExportRow.class,rows);
    }
    @Data public static class ExportRow {
        @ExcelProperty("字段") private String field;
        @ExcelProperty("内容") private String value;
    }
}
