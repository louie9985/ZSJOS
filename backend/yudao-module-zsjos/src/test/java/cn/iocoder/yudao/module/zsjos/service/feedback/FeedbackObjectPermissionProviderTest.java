package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.feedback.FeedbackRoundDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.feedback.FeedbackRoundMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackObjectPermissionProviderTest {

    private static final Long FEEDBACK_ID = 1L;
    private static final Long SUBMITTER_ID = 11L;
    private static final Long DEPARTMENT_LEADER_ID = 21L;
    private static final Long CHAIRMAN_ID = 12L;

    @Mock private FeedbackMapper feedbackMapper;
    @Mock private FeedbackRoundMapper roundMapper;
    @Mock private PermissionApi permissionApi;
    @InjectMocks private FeedbackObjectPermissionProvider provider;

    @Test
    void employeeActionsAreStrictlyLimitedToSubmitter() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(FeedbackConstants.TYPE_BUG, SUBMITTER_ID));

        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-own", SUBMITTER_ID));
        assertTrue(provider.hasPermission(FEEDBACK_ID, "reply-own", SUBMITTER_ID));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-own", CHAIRMAN_ID));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "survey-submit-own", CHAIRMAN_ID));
    }

    @Test
    void partnerSubjectDoesNotPassEmployeeOwnPermission() {
        FeedbackDO row = feedback(FeedbackConstants.TYPE_BUG, SUBMITTER_ID);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_PARTNER_ACCOUNT);
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(row);

        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-own", SUBMITTER_ID));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "reply-own", SUBMITTER_ID));
    }

    @Test
    void adminAccessUsesPermissionForTheRecordsActualType() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(FeedbackConstants.TYPE_SUPPORT, SUBMITTER_ID));
        when(permissionApi.hasAnyPermissions(21L, FeedbackConstants.PERMISSION_SUPPORT_MANAGE))
                .thenReturn(true);

        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-admin", 21L));
        assertTrue(provider.hasPermission(FEEDBACK_ID, "manage", 21L));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "unknown", 21L));
    }

    /**
     * 审批人不是单据本人，不能靠 read-own 放行；这条同时也是回归守卫——
     * 早先把两种口径合并的写法会让 submittersOnly 那条断言失效。
     */
    @Test
    void approverActionOnlyAdmitsTheSnapshottedApprovers() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(FeedbackConstants.TYPE_REQUIREMENT, SUBMITTER_ID));
        when(roundMapper.selectByFeedbackId(FEEDBACK_ID))
                .thenReturn(List.of(round(1, Map.of("departmentLeaderUserId", DEPARTMENT_LEADER_ID,
                        "chairmanUserId", CHAIRMAN_ID))));

        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-approver", DEPARTMENT_LEADER_ID));
        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-approver", CHAIRMAN_ID));
        // 提交人自己并不因为是提交人而自动获得审批人口径；无关账号更不行。
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", SUBMITTER_ID));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", 99L));
    }

    /**
     * 多轮审批：历史轮次的审批人回来翻单子仍应看得到，他当时确实审过。
     * 只看最新一轮会把这类人挡在门外。
     */
    @Test
    void approverActionLooksAcrossAllRoundsNotJustTheLatest() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(FeedbackConstants.TYPE_REQUIREMENT, SUBMITTER_ID));
        when(roundMapper.selectByFeedbackId(FEEDBACK_ID)).thenReturn(List.of(
                // 第 1 轮由 21 审（已完成），第 2 轮换成了别人。
                round(1, Map.of("departmentLeaderUserId", DEPARTMENT_LEADER_ID, "chairmanUserId", CHAIRMAN_ID)),
                round(2, Map.of("departmentLeaderUserId", 88L, "chairmanUserId", CHAIRMAN_ID))));

        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-approver", DEPARTMENT_LEADER_ID));
        assertTrue(provider.hasPermission(FEEDBACK_ID, "read-approver", 88L));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", 99L));
    }

    /** 快照缺失或损坏时一律不放行：宁可少放行，也不能把单据漏给解析异常波及的人。 */
    @Test
    void approverActionFailsClosedWhenSnapshotIsUnusable() {
        when(feedbackMapper.selectById(FEEDBACK_ID)).thenReturn(feedback(FeedbackConstants.TYPE_REQUIREMENT, SUBMITTER_ID));
        when(roundMapper.selectByFeedbackId(FEEDBACK_ID)).thenReturn(List.of(round(1, Map.of())));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", DEPARTMENT_LEADER_ID));

        FeedbackRoundDO broken = new FeedbackRoundDO();
        broken.setId(9L);
        broken.setApprovalContextJson("{ not json");
        when(roundMapper.selectByFeedbackId(FEEDBACK_ID)).thenReturn(List.of(broken));
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", DEPARTMENT_LEADER_ID));

        when(roundMapper.selectByFeedbackId(FEEDBACK_ID)).thenReturn(List.of());
        assertFalse(provider.hasPermission(FEEDBACK_ID, "read-approver", DEPARTMENT_LEADER_ID));
    }

    private FeedbackDO feedback(String type, Long submitterId) {
        FeedbackDO row = new FeedbackDO();
        row.setId(FEEDBACK_ID);
        row.setFeedbackType(type);
        row.setSubmitterSubjectType(FeedbackConstants.SUBJECT_ADMIN);
        row.setSubmitterUserId(submitterId);
        return row;
    }

    private FeedbackRoundDO round(int roundNo, Map<String, Object> context) {
        FeedbackRoundDO row = new FeedbackRoundDO();
        row.setId((long) roundNo);
        row.setFeedbackId(FEEDBACK_ID);
        row.setRoundNo(roundNo);
        row.setApprovalContextJson(JsonUtils.toJsonString(new LinkedHashMap<>(context)));
        return row;
    }
}
