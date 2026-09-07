package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadSubmitterFeedbackAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadSubmitterFeedbackMapper;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LeadSubmitterFeedbackServiceContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                var beans = context.getBeanFactory();
                beans.registerSingleton("feedbackMapper", mock(FeedbackMapper.class));
                beans.registerSingleton("leadSubmitterFeedbackMapper", mock(LeadSubmitterFeedbackMapper.class));
                beans.registerSingleton("leadMapper", mock(LeadMapper.class));
                beans.registerSingleton("attachmentMapper", mock(LeadSubmitterFeedbackAttachmentMapper.class));
                beans.registerSingleton("permission", mock(LeadSubmitterFeedbackPermissionProvider.class));
                beans.registerSingleton("identityPermission", mock(LeadObjectPermissionService.class));
                beans.registerSingleton("partnerAccountService", mock(PartnerAccountService.class));
                beans.registerSingleton("adminUserApi", mock(AdminUserApi.class));
                beans.registerSingleton("fileApi", mock(FileApi.class));
                beans.registerSingleton("publisher", mock(LeadNotifyEventPublisher.class));
            })
            .withBean(LeadSubmitterFeedbackService.class);

    @Test
    void leadSubmitterFeedbackMapperInjectsWhenFeedbackMapperBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            LeadSubmitterFeedbackService service = context.getBean(LeadSubmitterFeedbackService.class);
            assertThat(ReflectionTestUtils.getField(service, "leadSubmitterFeedbackMapper"))
                    .isSameAs(context.getBean(LeadSubmitterFeedbackMapper.class));
        });
    }
}
