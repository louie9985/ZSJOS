package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 考期日历")
@RestController
@RequestMapping("/zsjos/exam-calendar")
@Validated
public class ExamScheduleController {
    @Resource private ExamScheduleService service;

    @GetMapping("/page")
    @Operation(summary = "查询精确考期")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:query')")
    public CommonResult<PageResult<ExamScheduleRespVO>> page(@Valid ExamSchedulePageReqVO req) {
        return success(service.exactPage(req, getLoginUserId()));
    }

    @GetMapping("/rough")
    @Operation(summary = "查询粗略考期")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:query')")
    public CommonResult<PageResult<ExamScheduleRespVO>> rough(@Valid ExamSchedulePageReqVO req) {
        return success(service.roughPage(req, getLoginUserId()));
    }

    @GetMapping("/category-options")
    @Operation(summary = "查询启用产品分类")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:query')")
    public CommonResult<List<ExamCategoryOptionRespVO>> categoryOptions() {
        return success(service.categoryOptions());
    }

    @PostMapping("/create")
    @Operation(summary = "创建考期草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<Long> create(@Valid @RequestBody ExamScheduleSaveReqVO req) {
        return success(service.create(req, getLoginUserId()));
    }

    @GetMapping("/product-options")
    @Operation(summary = "查询考期可选产品及规格")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ExamProductScopeRespVO>> productOptions() {
        return success(service.productOptions(getLoginUserId()));
    }

    @PutMapping("/update/{id}")
    @Operation(summary = "修改考期")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<Boolean> update(@PathVariable Long id, @Valid @RequestBody ExamScheduleSaveReqVO req) {
        service.update(id, req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/publish/{id}")
    @Operation(summary = "发布考期")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<Boolean> publish(@PathVariable Long id) {
        service.publish(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/revoke/{id}")
    @Operation(summary = "撤销考期")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<Boolean> revoke(@PathVariable Long id) {
        service.revoke(id, getLoginUserId());
        return success(true);
    }
}
