package cn.iocoder.yudao.module.zsjos.controller.admin.contactnote;

import cn.iocoder.yudao.module.zsjos.controller.admin.contactnote.vo.ContactNoteVO.Append;
import cn.iocoder.yudao.module.zsjos.service.contactnote.ContactNoteService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementService;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactNotePermissionTest {
    public record Grants(Set<String> values) {
        public boolean hasAnyPermissions(String... ids) { return Arrays.stream(ids).anyMatch(values::contains); }
        public boolean hasPermission(String id) { return values.contains(id); }
    }

    @ParameterizedTest
    @CsvSource({"appendLead,zsjos:lead:query,zsjos:lead-follow-up:append,zsjos:student-contact:append",
                "appendStudent,zsjos:student:query-my,zsjos:student-contact:append,zsjos:lead-follow-up:append"})
    void writeNeedsViewAndMatchingAppendGrant(String method, String view, String append, String other) throws Exception {
        var expression = new SpelExpressionParser().parseExpression(ContactNoteController.class
                .getMethod(method, Long.class, Append.class).getAnnotation(PreAuthorize.class).value());
        for (var grants : java.util.List.of(Set.of(view), Set.of(append), Set.of(view, other), Set.of(view, append))) {
            var context = new StandardEvaluationContext();
            context.setBeanResolver((ctx, name) -> new Grants(grants));
            assertEquals(grants.contains(view) && grants.contains(append), expression.getValue(context, Boolean.class));
        }
    }

    @Test
    void outOfScopeSubjectPreventsWrites() {
        var controller = new ContactNoteController();
        var leads = mock(LeadManagementService.class);
        var students = mock(StudentContactService.class);
        var notes = mock(ContactNoteService.class);
        ReflectionTestUtils.setField(controller, "leads", leads);
        ReflectionTestUtils.setField(controller, "students", students);
        ReflectionTestUtils.setField(controller, "notes", notes);
        doThrow(SecurityException.class).when(leads).checkNoteSubject(eq(7L), any());
        doThrow(SecurityException.class).when(students).checkNoteSubject(eq(7L), any());
        var request = new Append("沟通", "request");
        assertThrows(SecurityException.class, () -> controller.appendLead(7L, request));
        assertThrows(SecurityException.class, () -> controller.appendStudent(7L, request));
        verifyNoInteractions(notes);
    }
}
