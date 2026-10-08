package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.*;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamCalendarNoteService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId;

@RestController
@RequestMapping("/zsjos/exam-calendar/note")
@Validated
public class ExamCalendarNoteController {
    @Resource private ExamCalendarNoteService service;
    @GetMapping
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:query')")
    public CommonResult<ExamCalendarNoteRespVO> get() {
        return success(service.get(getRequiredTenantId(), getLoginUserId()));
    }
    @PutMapping
    @ApiAccessLog(requestEnable = false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<ExamCalendarNoteRespVO> save(@Valid @RequestBody ExamCalendarNoteSaveReqVO request) {
        return success(service.save(getRequiredTenantId(), getLoginUserId(), request));
    }
    @PostMapping("/image")
    @ApiAccessLog(requestEnable = false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    public CommonResult<ExamCalendarNoteImageRespVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return success(service.upload(getRequiredTenantId(), getLoginUserId(), file));
    }
}
