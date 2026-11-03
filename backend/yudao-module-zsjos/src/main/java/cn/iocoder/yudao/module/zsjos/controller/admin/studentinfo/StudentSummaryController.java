package cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentSummaryService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/zsjos/student-summary")
public class StudentSummaryController {
    @Resource private StudentSummaryService service;
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/columns")
    @PreAuthorize("@ss.hasPermission('zsjos:student-summary:query')")
    public CommonResult<List<Column>> columns() { return success(service.columns()); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/classes")
    @PreAuthorize("@ss.hasPermission('zsjos:student-summary:query')")
    public CommonResult<List<ClassOption>> classes() { return success(service.classes()); }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:student-summary:query')")
    public CommonResult<PageResult<Row>> page(@Valid Query query, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(service.page(query));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @GetMapping("/coverage")
    @PreAuthorize("@ss.hasPermission('zsjos:student-summary:query')")
    public CommonResult<Coverage> coverage(@Valid Query query, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(service.coverage(query));
    }
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PostMapping("/export")
    @PreAuthorize("@ss.hasPermission('zsjos:student-summary:query') && @ss.hasPermission('zsjos:student-summary:export')")
    public void export(@Valid @RequestBody Export query, HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Cache-Control","no-store"); service.export(query,response);
    }
}
