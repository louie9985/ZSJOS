package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo.ExamScheduleAttachmentRespVO;
import cn.iocoder.yudao.module.zsjos.service.examcalendar.ExamScheduleAttachmentService;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/exam-calendar/attachment")
public class ExamScheduleAttachmentController {
    @Resource private ExamScheduleAttachmentService service;

    @GetMapping("/{scheduleId}/{fileId}")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:query')")
    @ApiAccessLog(responseEnable = false)
    public CommonResult<ExamScheduleAttachmentRespVO> read(@PathVariable Long scheduleId, @PathVariable Long fileId) {
        return success(service.read(scheduleId, fileId, getLoginUserId()));
    }

    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-calendar:manage')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<ExamScheduleAttachmentRespVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return success(service.upload(file, getLoginUserId()));
    }
}
