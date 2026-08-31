package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackObjectPermissionProviderTest {

    @Mock private FeedbackMapper feedbackMapper;
    @Mock private PermissionApi permissionApi;
    @InjectMocks private FeedbackObjectPermissionProvider provider;

    @Test
    void employeeActionsAreStrictlyLimitedToSubmitter() {
        when(feedbackMapper.selectById(1L)).thenReturn(feedback(FeedbackConstants.TYPE_BUG, 11L));

        assertTrue(provider.hasPermission(1L, "read-own", 11L));
        assertTrue(provider.hasPermission(1L, "reply-own", 11L));
        assertFalse(provider.hasPermission(1L, "read-own", 12L));
        assertFalse(provider.hasPermission(1L, "survey-submit-own", 12L));
    }

    @Test
    void partnerSubjectDoesNotPassEmployeeOwnPermission() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, 11L);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT);
        when(feedbackMapper.selectById(1L)).thenReturn(row);

        assertFalse(provider.hasPermission(1L, "read-own", 11L));
        assertFalse(provider.hasPermission(1L, "reply-own", 11L));
    }

    @Test
    void adminAccessUsesPermissionForTheRecordsActualType() {
        when(feedbackMapper.selectById(1L)).thenReturn(feedback(FeedbackConstants.TYPE_SUPPORT, 11L));
        when(permissionApi.hasAnyPermissions(21L, FeedbackConstants.PERMISSION_SUPPORT_MANAGE))
                .thenReturn(true);

        assertTrue(provider.hasPermission(1L, "read-admin", 21L));
        assertTrue(provider.hasPermission(1L, "manage", 21L));
        assertFalse(provider.hasPermission(1L, "unknown", 21L));
    }

    private FeedbackDO feedback(String type, Long submitterId) {
        FeedbackDO row = new FeedbackDO();
        row.setId(1L);
        row.setFeedbackType(type);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_ADMIN);
        row.setSubmitterUserId(submitterId);
        return row;
    }
}
