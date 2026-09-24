package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import java.util.*;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;

import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterFields.*;
import static cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterRelations.*;

final class SubordinateSalesFilterFields {
    private SubordinateSalesFilterFields() {}

    static void register(Map<String, Field> result) {
        Map<String, Binding> scene = bind("subordinate_sales", "1", null);
        add(result, text(Sensitivity.PERSONAL, "subordinate.name", IDENTITY, "姓名", scene));
        add(result, text(Sensitivity.PERSONAL, "subordinate.username", IDENTITY, "账号", scene));
        add(result, text(Sensitivity.PERSONAL, "subordinate.mobile", IDENTITY, "手机号", scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.accountStatus", STATUS, "账号状态", options(String.valueOf(CommonStatusEnum.ENABLE.getStatus()), "启用", String.valueOf(CommonStatusEnum.DISABLE.getStatus()), "停用"), scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.presence", STATUS, "在岗状态", options("online", "在线", "offline", "离线"), scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.accepting", STATUS, "接单状态", options("true", "开启", "false", "关闭"), scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.eligible", STATUS, "接单资格", options("true", "具备资格", "false", "暂无资格"), scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.newcomerPoolStatus", STATUS, "新人池状态", options("active", "启用", "inactive", "未启用"), scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayPendingCount", "业务指标", "今日待办数", scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.todayFollowUpStatus", "业务指标", "今日跟进状态", options("completed", "已完成", "pending", "待完成"), scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.firstFollowTimeoutCount", "业务指标", "首次跟进超时数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.suspendedLeadCount", "业务指标", "挂起客资数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.validLeadCount", "业务指标", "有效客资数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.convertedLeadCount", "业务指标", "成交客资数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.effectiveOrderCount", "业务指标", "生效订单数", scene));
        add(result, number(Sensitivity.FINANCIAL, "subordinate.effectiveOrderAmount", "业务指标", "生效订单金额", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayAssignedCount", "业务指标", "今日分配数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayMissedCount", "业务指标", "今日漏接数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayReceivedCount", "业务指标", "今日接单数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayQualifiedCount", "业务指标", "今日判定有效数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayFollowUpRecordCount", "业务指标", "今日跟进记录数", scene));
        add(result, number(Sensitivity.FINANCIAL, "subordinate.todayOrderAmount", "业务指标", "今日订单金额", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.pendingQualificationCount", "业务指标", "待判定数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayFollowUpTotalCount", "业务指标", "今日应跟进数", scene));
        add(result, number(Sensitivity.STANDARD, "subordinate.todayFollowUpRemainingCount", "业务指标", "今日剩余跟进数", scene));
        add(result, select(Sensitivity.STANDARD, "subordinate.canReceiveNewLeads", STATUS, "当前可接新客资", options("true", "是", "false", "否"), scene));
    }


}
