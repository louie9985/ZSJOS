package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentArchiveService;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/student/service/{relationId}/archive")
public class StudentArchiveController {
    @Resource private StudentArchiveService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentArchiveExtraService extra;
    @GetMapping("/export")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:export')")
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public void export(@PathVariable Long relationId,HttpServletResponse response) throws java.io.IOException {
        var info=service.export(relationId).getInformation();
        var rows=new java.util.ArrayList<>(info.getFields().stream().map(field->{
            var row=new ExportRow();
            row.setField(field.getLabel()); row.setValue("images".equals(field.getType())
                ? info.getImages().stream().filter(i->field.getKey().equals(i.getFieldKey())).map(StudentInfoVO.Image::getName).collect(java.util.stream.Collectors.joining("；"))
                : info.getValues().getOrDefault(field.getKey(),"")); return row;
        }).toList());
        var additional=extra.detail(relationId);
        for (var field:additional.getFields()) {
            var value=additional.getValues().get(field.key());
            var row=new ExportRow(); row.setField(field.label());
            var parts=new java.util.ArrayList<String>();
            if (value!=null) {
                parts.add(value.getText()); parts.addAll(value.getLinks());
                additional.getAttachments().stream().filter(a->value.getAttachmentIds().contains(a.getId())).map(StudentInfoVO.Image::getName).forEach(parts::add);
            }
            row.setValue(String.join("；",parts)); rows.add(row);
        }
        cn.iocoder.yudao.framework.excel.core.util.ExcelUtils.write(response,"学员档案.xlsx","学员档案",ExportRow.class,rows);
    }
    @lombok.Data public static class ExportRow {
        @cn.idev.excel.annotation.ExcelProperty("字段") private String field;
        @cn.idev.excel.annotation.ExcelProperty("内容") private String value;
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read')")
    public CommonResult<Detail> detail(@PathVariable Long relationId) { return success(service.detail(relationId,false)); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/sensitive")
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:sensitive-read')")
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ)
    public CommonResult<Detail> sensitive(@PathVariable Long relationId) { return success(service.detail(relationId,true)); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/edit")
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:update')")
    public CommonResult<Edit> edit(@PathVariable Long relationId) { return success(service.edit(relationId)); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PostMapping
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:update')")
    public CommonResult<Integer> save(@PathVariable Long relationId,@Valid @RequestBody Save request) { return success(service.save(relationId,request)); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PostMapping("/images")
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:update') && @ss.hasPermission('zsjos:student-archive:sensitive-read')")
    public CommonResult<StudentInfoVO.Image> upload(@PathVariable Long relationId,@RequestParam String fieldKey,
            @RequestParam(defaultValue="") String side,@RequestParam MultipartFile file) { return success(service.upload(relationId,fieldKey,side,file)); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/images/{imageId}")
    @PreAuthorize("@ss.hasPermission('zsjos:student-archive:read') && @ss.hasPermission('zsjos:student-archive:sensitive-read')")
    public byte[] image(@PathVariable Long relationId,@PathVariable Long imageId,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Content-Type-Options","nosniff");
        response.setContentType("application/octet-stream"); return service.image(relationId,imageId);
    }
}
