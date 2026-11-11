package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentSummaryVO.*;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionSummaryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/zsjos/exam-collection/{taskId}/summary")
public class ExamCollectionSummaryController {
    @Resource private ExamCollectionSummaryService service;
    @GetMapping("/columns") @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<List<Column>> columns(@PathVariable Long taskId,HttpServletResponse response) { noStore(response);return success(service.columns(taskId)); }
    @GetMapping("/classes") @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<List<ClassOption>> classes(@PathVariable Long taskId,HttpServletResponse response) { noStore(response);return success(service.classes(taskId)); }
    @GetMapping("/page") @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Row>> page(@PathVariable Long taskId,@Valid Query query,HttpServletResponse response) { noStore(response);return success(service.page(taskId,query)); }
    @GetMapping("/coverage") @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<Coverage> coverage(@PathVariable Long taskId,@Valid Query query,HttpServletResponse response) { noStore(response);return success(service.coverage(taskId,query)); }
    @PostMapping("/export") @ApiAccessLog(requestEnable=false,responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public void export(@PathVariable Long taskId,@Valid @RequestBody Export query,HttpServletResponse response)throws java.io.IOException { noStore(response);service.export(taskId,query,response); }
    private void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
}
