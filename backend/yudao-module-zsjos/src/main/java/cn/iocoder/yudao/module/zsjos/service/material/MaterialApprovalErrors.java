package cn.iocoder.yudao.module.zsjos.service.material;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
public interface MaterialApprovalErrors {
    ErrorCode INVALID_TASK = new ErrorCode(1_900_020_041, "该素材审批任务不存在或不属于当前用户");
    ErrorCode STALE_SNAPSHOT = new ErrorCode(1_900_020_042, "素材版本已重新编辑或提交，无法展示本次审批的原始快照");
    ErrorCode TASK_CHANGED = new ErrorCode(1_900_020_043, "素材审批状态已变化，请刷新待办");
}
