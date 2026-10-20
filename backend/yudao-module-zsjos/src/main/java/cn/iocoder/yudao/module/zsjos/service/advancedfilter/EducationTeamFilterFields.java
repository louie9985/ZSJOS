package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class EducationTeamFilterFields {
    private EducationTeamFilterFields() {}

    static void register(Map<String, Field> result) {
        Map<String, Binding> scene = bind("education_team", "1", null);
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayNewIntentionCount", "业务指标", "新意向", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayOldIntentionFollowUpRecordCount", "业务指标", "老意向跟进次数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayStudentContactCount", "业务指标", "今日学员联系数", scene));
        add(result, text(Sensitivity.PERSONAL, "educationTeam.name", IDENTITY, "姓名", scene));
        add(result, text(Sensitivity.PERSONAL, "educationTeam.username", IDENTITY, "账号", scene));
        add(result, text(Sensitivity.PERSONAL, "educationTeam.mobile", IDENTITY, "手机号", scene));
        add(result, select(Sensitivity.STANDARD, "educationTeam.accountStatus", STATUS, "账号状态", options(String.valueOf(CommonStatusEnum.ENABLE.getStatus()), "启用", String.valueOf(CommonStatusEnum.DISABLE.getStatus()), "停用"), scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayPendingCount", "业务指标", "今日待办数", scene));
        add(result, select(Sensitivity.STANDARD, "educationTeam.todayFollowUpStatus", "业务指标", "今日跟进状态", options("completed", "已完成", "incomplete", "待完成"), scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.firstFollowTimeoutCount", "业务指标", "首次跟进超时数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.suspendedLeadCount", "业务指标", "挂起客资数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.validLeadCount", "业务指标", "有效客资数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.convertedLeadCount", "业务指标", "成交客资数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.effectiveOrderCount", "业务指标", "生效订单数", scene));
        add(result, number(Sensitivity.FINANCIAL, "educationTeam.effectiveOrderAmount", "业务指标", "生效订单金额", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayReceivedCount", "业务指标", "今日接单数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayQualifiedCount", "业务指标", "今日判定有效数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayFollowUpRecordCount", "业务指标", "今日跟进记录数", scene));
        add(result, number(Sensitivity.FINANCIAL, "educationTeam.todayOrderAmount", "业务指标", "今日订单金额", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.pendingQualificationCount", "业务指标", "待判定数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayFollowUpTotalCount", "业务指标", "今日应跟进数", scene));
        add(result, number(Sensitivity.STANDARD, "educationTeam.todayFollowUpRemainingCount", "业务指标", "今日剩余跟进数", scene));
    }


}
