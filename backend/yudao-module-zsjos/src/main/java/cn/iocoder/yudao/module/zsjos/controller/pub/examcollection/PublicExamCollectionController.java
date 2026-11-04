package cn.iocoder.yudao.module.zsjos.controller.pub.examcollection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.controller.pub.examcollection.vo.ExamCollectionPublicVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Image;
import cn.iocoder.yudao.module.zsjos.service.examcollection.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoUploadRateLimit;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/exam-collection")
@PermitAll
@TenantIgnore
public class PublicExamCollectionController {
    @Resource private ExamCollectionPublicService service;
    @Resource private ExamCollectionAttachmentService attachments;
    @Resource private StudentInfoUploadRateLimit rateLimit;
    @GetMapping("/detail")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<ExamCollectionPublicVO.Runtime> detail(@RequestHeader("X-Exam-Token") String token, HttpServletResponse response) {
        noStore(response); return success(service.detail(token));
    }
    @PostMapping("/session")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<ExamCollectionPublicVO.Session> start(@RequestHeader("X-Exam-Token") String token,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response); rateLimit.checkExamSession(token, request.getRemoteAddr()); return success(service.start(token));
    }
    @PostMapping("/submit")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Boolean> submit(@RequestHeader("X-Exam-Token") String token,
            @RequestHeader("X-Exam-Session") String session, @Valid @RequestBody ExamCollectionPublicVO.Submit input,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response); rateLimit.check(session, request.getRemoteAddr()); service.submit(token, session, input); return success(true);
    }
    @PostMapping("/attachments")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Image> upload(@RequestHeader("X-Exam-Token") String token, @RequestHeader("X-Exam-Session") String session,
            @RequestHeader("X-Exam-Upload-Key") String requestKey,
            @RequestParam String fieldKey, @RequestParam(defaultValue="") String side, @RequestParam MultipartFile file,
            HttpServletRequest request, HttpServletResponse response) {
        noStore(response); rateLimit.check(session, request.getRemoteAddr()); return success(attachments.upload(token, session, fieldKey, side, file,requestKey));
    }
    @DeleteMapping("/attachments/{id}")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<Boolean> remove(@RequestHeader("X-Exam-Token") String token, @RequestHeader("X-Exam-Session") String session,
            @PathVariable Long id, HttpServletResponse response) {
        noStore(response); attachments.remove(token, session, id); return success(true);
    }
    @GetMapping("/attachments/{id}")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public byte[] read(@RequestHeader("X-Exam-Token") String token, @RequestHeader("X-Exam-Session") String session,
            @PathVariable Long id, HttpServletResponse response) {
        noStore(response); response.setHeader("X-Content-Type-Options", "nosniff"); response.setContentType("application/octet-stream");
        return attachments.read(token, session, id);
    }
    private void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
}
