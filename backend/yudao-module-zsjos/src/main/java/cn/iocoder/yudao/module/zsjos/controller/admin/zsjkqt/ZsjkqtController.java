package cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.zsjkqt.vo.ZsjkqtVO.*;
import cn.iocoder.yudao.module.zsjos.service.zsjkqt.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController
@RequestMapping("/zsjos/zsjkqt")
@Validated
@PreAuthorize("@ss.hasPermission('zsjos:zsjkqt:access')")
public class ZsjkqtController {
    @Resource private ZsjkqtService service;
    @Resource private ZsjkqtExcelEngine excel;
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/drafts")
    public CommonResult<PageResult<Draft>> page(@RequestParam(defaultValue="1") @Min(1) int pageNo,
    @RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,
    @RequestParam(required=false) @Size(max=120) String keyword,
    @RequestParam(required=false) @Min(2000) @Max(2099) Integer year,
    @RequestParam(required=false) @Min(1) @Max(12) Integer month) {
        return success(service.page(pageNo, pageSize, keyword, year, month));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/drafts/{id}") public CommonResult<Draft> get(@PathVariable Long id) {
        return success(service.get(id));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PostMapping("/drafts") public CommonResult<Draft> create(@Valid @RequestBody Save body) {
        return success(service.create(body));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PutMapping("/drafts/{id}") public CommonResult<Draft> save(@PathVariable Long id, @Valid @RequestBody Save body) {
        return success(service.save(id, body));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PutMapping("/drafts/{id}/name") public CommonResult<Boolean> rename(@PathVariable Long id, @Valid @RequestBody Rename body) {
        service.rename(id, body);
        return success(true);
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @DeleteMapping("/drafts/{id}") public CommonResult<Boolean> delete(@PathVariable Long id, @RequestParam @Min(0) int version) {
        service.delete(id, version);
        return success(true);
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/config/{key}") public CommonResult<Config> config(@PathVariable @Pattern(regexp="schedules|profiles") String key) {
        return success(service.config(key));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PutMapping("/config/schedules") public CommonResult<Config> schedules(@Valid @RequestBody ConfigSave body) {
        return success(service.saveSchedules(body));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PutMapping("/profiles") public CommonResult<Boolean> profile(@Valid @RequestBody Profile body) {
        service.profile(body);
        return success(true);
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.READ_ONLY)
    @PostMapping("/parse/{kind}") public CommonResult<Map<String, Object>> parse(@PathVariable @Pattern(regexp="source|report") String kind, @RequestParam MultipartFile file) {
        if(file.isEmpty()||file.getSize()>20*1024*1024||file.getOriginalFilename()==null||!file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))throw exception(ZsjkqtErrors.INVALID, "请选择不超过 20MB 的 .xlsx 文件");
        try {
            return success(kind.equals("source")?excel.parseSource(file.getBytes(), file.getOriginalFilename()):excel.parseReport(file.getBytes(), file.getOriginalFilename()));
        }
        catch(java.io.IOException|IllegalArgumentException e) {
            throw exception(ZsjkqtErrors.EXCEL, e.getMessage());
        }
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @ZsjosAudit(mode=ZsjosAudit.Mode.SENSITIVE_READ, action="zsjkqt-export", targetType="zsjkqt-draft")
    @GetMapping("/drafts/{id}/export/{kind}") public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam @Min(0) int version, @PathVariable @Pattern(regexp="report|issues") String kind) {
        byte[] bytes=service.export(id, version, kind.equals("issues"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("zsjkqt-"+kind+".xlsx").build().toString()).body(bytes);
    }
}
