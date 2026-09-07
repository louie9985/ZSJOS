package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.notify.NotifyBusinessEventApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackConfigMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackNoDailyCounterMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackReplyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackSurveyMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.WorkOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FeedbackServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean("configMapper", Runnable.class, () -> mock(Runnable.class))
            .withBean("feedbackMapper", FeedbackMapper.class, () -> mock(FeedbackMapper.class))
            .withBean("roundMapper", FeedbackRoundMapper.class, () -> mock(FeedbackRoundMapper.class))
            .withBean("replyMapper", FeedbackReplyMapper.class, () -> mock(FeedbackReplyMapper.class))
            .withBean("surveyMapper", FeedbackSurveyMapper.class, () -> mock(FeedbackSurveyMapper.class))
            .withBean("feedbackConfigMapper", FeedbackConfigMapper.class, () -> mock(FeedbackConfigMapper.class))
            .withBean("counterMapper", FeedbackNoDailyCounterMapper.class,
                    () -> mock(FeedbackNoDailyCounterMapper.class))
            .withBean("workOrderMapper", WorkOrderMapper.class, () -> mock(WorkOrderMapper.class))
            .withBean("historyMapper", WorkOrderHistoryMapper.class, () -> mock(WorkOrderHistoryMapper.class))
            .withBean("dynamicFormService", FeedbackDynamicFormService.class,
                    () -> mock(FeedbackDynamicFormService.class))
            .withBean("definitionReadApi", BpmDefinitionReadApi.class, () -> mock(BpmDefinitionReadApi.class))
            .withBean("processInstanceApi", BpmProcessInstanceApi.class, () -> mock(BpmProcessInstanceApi.class))
            .withBean("dictDataApi", DictDataApi.class, () -> mock(DictDataApi.class))
            .withBean("adminUserApi", AdminUserApi.class, () -> mock(AdminUserApi.class))
            .withBean("deptApi", DeptApi.class, () -> mock(DeptApi.class))
            .withBean("roleApi", RoleApi.class, () -> mock(RoleApi.class))
            .withBean("permissionApi", PermissionApi.class, () -> mock(PermissionApi.class))
            .withBean("fileApi", FileApi.class, () -> mock(FileApi.class))
            .withBean("partnerMapper", PartnerMapper.class, () -> mock(PartnerMapper.class))
            .withBean("notifyBusinessEventApi", NotifyBusinessEventApi.class,
                    () -> mock(NotifyBusinessEventApi.class))
            .withBean(FeedbackServiceImpl.class);

    @Test
    void feedbackConfigMapperInjectsWhenUnrelatedConfigMapperBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FeedbackServiceImpl.class);
            assertThat(context).hasSingleBean(FeedbackConfigMapper.class);
        });
    }
}
