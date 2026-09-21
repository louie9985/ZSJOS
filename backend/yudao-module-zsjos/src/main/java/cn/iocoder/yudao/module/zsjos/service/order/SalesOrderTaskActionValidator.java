package cn.iocoder.yudao.module.zsjos.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_REJECT_REASON_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;

/** 普通成交审核的驳回原因约束同时覆盖业务接口和通用 BPM 入口。 */
@Component
public class SalesOrderTaskActionValidator implements BpmTaskActionValidator {
    @Override
    public void validate(BpmTaskActionContext context) {
        if (PROCESS_DEFINITION_KEY.equals(context.getProcessDefinitionKey())
                && (TASK_REGISTRATION.equals(context.getTaskDefinitionKey()) || TASK_FINANCE.equals(context.getTaskDefinitionKey()))
                && (ACTION_REJECT.equals(context.getAction())
                    || (ACTION_APPROVE.equals(context.getAction()) && StrUtil.isNotBlank(context.getParentTaskId())))
                && StrUtil.isBlank(context.getReason())) {
            if (ACTION_REJECT.equals(context.getAction())) throw exception(SALES_ORDER_REJECT_REASON_REQUIRED);
            // 主管子任务沿用原意见必填规则，不继承普通中心的新选填策略。
            throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SALES_ORDER_SUPERVISOR_REASON_REQUIRED);
        }
    }
}
