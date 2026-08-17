package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

class BpmProcessInstanceApiImplPartnerTest {

    @Test
    void typedBusinessTerminationRejectsBlankAuditParameters() {
        BpmProcessInstanceApiImpl api = new BpmProcessInstanceApiImpl();
        BpmProcessInstanceService service = mock(BpmProcessInstanceService.class);
        ReflectionTestUtils.setField(api, "processInstanceService", service);

        assertThrows(IllegalArgumentException.class, () -> api.terminateProcessInstanceByBusiness(
                new BpmStartSubjectDTO(UserTypeEnum.PARTNER.getValue(), 20L), "process-1", " ", "reason"));
        verifyNoInteractions(service);
    }

    @Test
    void externalSubjectErrorsHaveUniqueCodes() {
        assertNotEquals(PROCESS_INSTANCE_HTTP_CALL_ERROR.getCode(), PROCESS_INSTANCE_EXTERNAL_USER_INVALID.getCode());
        assertNotEquals(PROCESS_INSTANCE_APPROVE_USER_SELECT_ASSIGNEES_NOT_CONFIG.getCode(),
                PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode());
        assertNotEquals(PROCESS_INSTANCE_EXTERNAL_USER_INVALID.getCode(),
                PROCESS_INSTANCE_EXTERNAL_CANDIDATE_UNSUPPORTED.getCode());
    }

    @Test
    void processInstanceErrorCodesAreUnique() throws IllegalAccessException {
        List<Integer> codes = Arrays.stream(cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.class.getFields())
                .filter(field -> field.getName().startsWith("PROCESS_INSTANCE_"))
                .map(this::errorCode).toList();

        assertEquals(codes.size(), codes.stream().distinct().count());
    }

    private Integer errorCode(Field field) {
        try {
            return ((cn.iocoder.yudao.framework.common.exception.ErrorCode) field.get(null)).getCode();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
