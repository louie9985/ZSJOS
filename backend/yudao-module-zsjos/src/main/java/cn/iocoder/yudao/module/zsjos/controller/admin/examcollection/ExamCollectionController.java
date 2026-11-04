package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionTaskService;
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
@RequestMapping("/zsjos/exam-collection")
public class ExamCollectionController {
    @Resource private ExamCollectionTaskService service;
    @GetMapping("/fields")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<List<Field>> fields(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.fields());
    }
    @PostMapping("/create")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query') && @ss.hasPermission('zsjos:exam-collection:create')")
    public CommonResult<Detail> create(@Valid @RequestBody Create request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.create(request));
    }
    @GetMapping("/page")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Task>> page(@Valid Query request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.page(request));
    }
    @GetMapping("/detail")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<Detail> detail(@RequestParam Long id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(service.detail(id));
    }
}
