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
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionRosterService roster;
    @GetMapping("/{id}/class-options")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<List<ClassOption>> classOptions(@PathVariable Long id, HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); return success(roster.classOptions(id));
    }
    @PostMapping("/{id}/classes")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:members')")
    public CommonResult<Boolean> adjustClasses(@PathVariable Long id,@Valid @RequestBody ClassChange request) {
        roster.adjustClasses(id,request); return success(true);
    }
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionResolutionService resolution;
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionQueryService queries;
    @Resource private cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionCorrectionService correction;
    @GetMapping("/{id}/submissions/{submissionId}/correction-preview")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:resolve')")
    public CommonResult<CorrectionPreview> correctionPreview(@PathVariable Long id, @PathVariable Long submissionId,
            @RequestParam Long serviceRelationId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(correction.preview(id, submissionId, serviceRelationId));
    }
    @PostMapping("/{id}/submissions/{submissionId}/correct")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:resolve')")
    public CommonResult<Boolean> correct(@PathVariable Long id, @PathVariable Long submissionId, @Valid @RequestBody Correction request) {
        correction.correct(id, submissionId, request); return success(true);
    }
    @PostMapping("/{id}/members")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:members')")
    public CommonResult<Boolean> adjust(@PathVariable Long id, @Valid @RequestBody RosterChange request) {
        roster.adjust(id, request); return success(true);
    }
    @GetMapping("/{id}/members")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Member>> members(@PathVariable Long id, @Valid MemberQuery query, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.members(id, query));
    }
    @GetMapping("/{id}/submissions")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Submission>> submissions(@PathVariable Long id, @Valid SubmissionQuery query, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.submissions(id, query));
    }
    @GetMapping("/{id}/submissions/{submissionId}")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<SubmissionDetail> submission(@PathVariable Long id, @PathVariable Long submissionId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.submission(id, submissionId));
    }
    @GetMapping("/{id}/submissions/{submissionId}/attachments/{fileId}")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public byte[] attachment(@PathVariable Long id, @PathVariable Long submissionId, @PathVariable Long fileId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("application/octet-stream"); return queries.attachment(id, submissionId, fileId);
    }
    @GetMapping("/{id}/candidates")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Candidate>> candidates(@PathVariable Long id, @Valid CandidateQuery query, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.candidates(id, query));
    }
    @GetMapping("/{id}/archive/{relationId}")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<Archive> archive(@PathVariable Long id, @PathVariable Long relationId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.archive(id, relationId));
    }
    @GetMapping("/{id}/events")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<PageResult<Event>> events(@PathVariable Long id, @Valid PageParam query, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(queries.events(id, query));
    }
    @PostMapping("/{id}/submissions/{submissionId}/resolve")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:resolve')")
    public CommonResult<Boolean> resolve(@PathVariable Long id, @PathVariable Long submissionId, @Valid @RequestBody Resolve request) {
        resolution.resolve(id, submissionId, request); return success(true);
    }
    @PostMapping("/{id}/submissions/{submissionId}/invalidate")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:resolve')")
    public CommonResult<Boolean> invalidate(@PathVariable Long id, @PathVariable Long submissionId, @Valid @RequestBody Invalidate request) {
        resolution.invalidate(id, submissionId, request); return success(true);
    }
    @GetMapping("/classes")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    public CommonResult<List<ClassOption>> classes(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return success(roster.classes());
    }
    @PostMapping("/{id}/notify")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:notify')")
    public CommonResult<Boolean> notifyClasses(@PathVariable Long id, @Valid @RequestBody Notify request) {
        roster.notifyClasses(id, request); return success(true);
    }
    @PostMapping("/{id}/complete")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:complete')")
    public CommonResult<Boolean> complete(@PathVariable Long id, @Valid @RequestBody Transition request) {
        roster.complete(id, request); return success(true);
    }
    @PostMapping("/{id}/reopen")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:reopen')")
    public CommonResult<Boolean> reopen(@PathVariable Long id, @Valid @RequestBody Transition request) {
        roster.reopen(id, request); return success(true);
    }
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
