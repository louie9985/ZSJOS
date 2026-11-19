package cn.iocoder.yudao.module.zsjos.controller.admin.contactnote;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.zsjos.controller.admin.contactnote.vo.ContactNoteVO.*;
import cn.iocoder.yudao.module.zsjos.service.contactnote.ContactNoteService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/zsjos/contact-note")
public class ContactNoteController {
    private static final String LEAD_READ = "@ss.hasAnyPermissions('zsjos:lead:query','zsjos:subordinate-sales:query',"
            + "'zsjos:partner:query','zsjos:partner:manage','zsjos:partner:manage-all',"
            + "'zsjos:student:query-my','zsjos:media-student:query-my','zsjos:sales-order:query',"
            + "'zsjos:sales-order:review','zsjos:lead-detail:follow-up-read','zsjos:lead-detail:appeal-read',"
            + "'zsjos:lead-detail:complaint-read','zsjos:lead-detail:order-read','zsjos:lead-detail:flow-read')";
    private static final String STUDENT_READ = "@ss.hasAnyPermissions('zsjos:student:query-my','zsjos:media-student:query-my')";
    private static final String LEAD_APPEND = "zsjos:lead-follow-up:append";
    private static final String STUDENT_APPEND = "zsjos:student-contact:append";
    @Resource private LeadManagementService leads;
    @Resource private StudentContactService students;
    @Resource private ContactNoteService notes;

    @GetMapping("/lead/{id}")
    @PreAuthorize(LEAD_READ)
    public CommonResult<Page> leadPage(@PathVariable Long id, @Valid PageParam page) {
        leads.checkNoteSubject(id, getLoginUserId());
        return success(notes.page("lead", id, page, LEAD_APPEND));
    }

    @PostMapping("/lead/{id}")
    @PreAuthorize(LEAD_READ + " && @ss.hasPermission('" + LEAD_APPEND + "')")
    public CommonResult<Long> appendLead(@PathVariable Long id, @Valid @RequestBody Append request) {
        leads.checkNoteSubject(id, getLoginUserId());
        return success(notes.append("lead", id, request));
    }

    @GetMapping("/student/{id}")
    @PreAuthorize(STUDENT_READ)
    public CommonResult<Page> studentPage(@PathVariable Long id, @Valid PageParam page) {
        students.checkNoteSubject(id, getLoginUserId());
        return success(notes.page("student", id, page, STUDENT_APPEND));
    }

    @PostMapping("/student/{id}")
    @PreAuthorize(STUDENT_READ + " && @ss.hasPermission('" + STUDENT_APPEND + "')")
    public CommonResult<Long> appendStudent(@PathVariable Long id, @Valid @RequestBody Append request) {
        students.checkNoteSubject(id, getLoginUserId());
        return success(notes.append("student", id, request));
    }
}
