package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class MaterialTaskActionValidatorTest {
    @Test void genericHookCoversBothViralProcessesAndLeavesOtherDomainsAlone(){
        var validator=new MaterialTaskActionValidator();var approvals=mock(MaterialApprovalService.class);
        ReflectionTestUtils.setField(validator,"approvals",approvals);
        for(String key:java.util.List.of("zsjos_viral_account_review","zsjos_viral_content_review")){
            var context=new BpmTaskActionContext().setAction("APPROVE").setProcessDefinitionKey(key);
            validator.validate(context);verify(approvals).validateTaskAction(context);
        }
        clearInvocations(approvals);
        validator.validate(new BpmTaskActionContext().setAction("APPROVE").setProcessDefinitionKey("other_process"));
        verifyNoInteractions(approvals);
    }
}
