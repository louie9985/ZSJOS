package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmStartSubjectDTO;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRespVO;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BpmPartnerStartSubjectConvertTest {

    @Test
    void processMessageKeepsPartnerStartSubject() {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getStartUserId()).thenReturn("3:20");
        when(instance.getId()).thenReturn("process-1");
        when(instance.getName()).thenReturn("提现审批");

        BpmStartSubjectDTO subject = BpmProcessInstanceConvert.INSTANCE
                .buildProcessInstanceApproveMessage(instance).getStartSubject();

        assertEquals(UserTypeEnum.PARTNER.getValue(), subject.getUserType());
        assertEquals(20L, subject.getUserId());
    }

    @Test
    void modelAndTaskViewsUseExternalDisplayNameWithoutParsingTypedIdAsLong() {
        UserSimpleBaseVO modelUser = BpmProcessInstanceConvert.INSTANCE.buildStartUser(
                "3:20", Map.of("externalStartUserName", "合作方甲"), Map.of(), Map.of());
        UserSimpleBaseVO taskUser = BpmTaskConvert.INSTANCE.buildStartUser(
                "3:20", Map.of("externalStartUserName", "合作方甲"), null);

        assertEquals("合作方甲", modelUser.getNickname());
        assertEquals("合作方甲", taskUser.getNickname());
        assertNull(BpmProcessInstanceConvert.INSTANCE.buildUser("3:20", Map.of(), Map.of()));
    }

    @Test
    void processPageSupportsPartnerWithImmutableEmptyAdminMap() {
        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(instance.getId()).thenReturn("process-1");
        when(instance.getStartUserId()).thenReturn("3:20");
        when(instance.getProcessVariables()).thenReturn(Map.of("externalStartUserName", "合作方甲"));

        PageResult<BpmProcessInstanceRespVO> result = BpmProcessInstanceConvert.INSTANCE.buildProcessInstancePage(
                new PageResult<>(List.of(instance), 1L), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                Map.of(), new HashMap<>(), new HashMap<>());

        assertEquals("合作方甲", result.getList().getFirst().getStartUser().getNickname());
    }
}
