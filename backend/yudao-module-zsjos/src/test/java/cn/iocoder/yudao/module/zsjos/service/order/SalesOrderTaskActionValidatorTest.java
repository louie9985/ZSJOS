package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;

class SalesOrderTaskActionValidatorTest {
    private final SalesOrderTaskActionValidator validator = new SalesOrderTaskActionValidator();
    @Test
    void allDefinitionVersionsUseOptionalOrdinaryApprovalButRequiredSupervisorApproval() {
        for (String taskKey : new String[]{TASK_REGISTRATION, TASK_FINANCE}) {
            for (int version : new int[]{1, 2, 3, 99}) {
                var context = new BpmTaskActionContext().setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
                        .setTaskDefinitionKey(taskKey).setProcessDefinitionVersion(version);
                assertEquals(false, validator.approvalReasonRequired(context));
                context.setParentTaskId("supervisor-parent");
                assertEquals(true, validator.approvalReasonRequired(context));
                context.setProcessDefinitionKey("other-process");
                assertNull(validator.approvalReasonRequired(context));
            }
        }
        assertNull(validator.approvalReasonRequired(new BpmTaskActionContext()
                .setProcessDefinitionKey(PROCESS_DEFINITION_KEY).setTaskDefinitionKey("other-node")));
    }

    @Test
    void bothCentersRequireRejectReasonButAllowEmptyApproval() {
        for (String key : new String[]{TASK_FINANCE, TASK_REGISTRATION}) {
            for (String reason : new String[]{null, "", "   "}) {
                var context = new BpmTaskActionContext().setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
                        .setTaskDefinitionKey(key).setAction("REJECT").setReason(reason);
                assertThrows(ServiceException.class, () -> validator.validate(context));
                context.setAction("APPROVE");
                assertDoesNotThrow(() -> validator.validate(context));
                context.setParentTaskId("parent");
                assertThrows(ServiceException.class, () -> validator.validate(context));
                context.setParentTaskId(null);
                context.setAction("REJECT").setReason("需要补正");
                assertDoesNotThrow(() -> validator.validate(context));
                context.setProcessDefinitionKey("unrelated").setReason("");
                assertDoesNotThrow(() -> validator.validate(context));
            }
        }
    }
}
