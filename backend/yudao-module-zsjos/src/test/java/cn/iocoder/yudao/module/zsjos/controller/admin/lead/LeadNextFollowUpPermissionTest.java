package cn.iocoder.yudao.module.zsjos.controller.admin.lead;

import cn.iocoder.yudao.module.zsjos.controller.admin.task.vo.BusinessTaskCancelReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LeadNextFollowUpPermissionTest {
    @Test
    void cancellationUsesOnlyItsDedicatedPermission() throws Exception {
        String expression = LeadFollowUpController.class
                .getMethod("cancelNextFollowUp", Long.class, BusinessTaskCancelReqVO.class)
                .getAnnotation(PreAuthorize.class).value();
        assertEquals("@ss.hasPermission('zsjos:lead-follow-up:cancel-next-time')", expression);
        for (Set<String> permissions : java.util.List.of(
                Set.of("zsjos:lead-follow-up:cancel-next-time"),
                Set.of("zsjos:lead-follow-up:create", "zsjos:business-task:cancel"), Set.<String>of())) {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setBeanResolver((evaluationContext, beanName) -> new PermissionCheck(permissions));
            assertEquals(permissions.contains("zsjos:lead-follow-up:cancel-next-time"),
                    new SpelExpressionParser().parseExpression(expression).getValue(context, Boolean.class));
        }
    }

    public record PermissionCheck(Set<String> permissions) {
        public boolean hasPermission(String permission) {
            return permissions.contains(permission);
        }
    }
}
