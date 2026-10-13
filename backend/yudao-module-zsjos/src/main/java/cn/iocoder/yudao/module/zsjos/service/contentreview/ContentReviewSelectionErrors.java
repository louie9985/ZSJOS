package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ContentReviewSelectionErrors {
    ErrorCode SELECTION_REQUIRED = new ErrorCode(1_900_012_140, "请选择本次审核编导并确认流程版本后提交");
    ErrorCode DEFINITION_CHANGED = new ErrorCode(1_900_012_141, "审核流程版本已变化，请重新加载并确认审核编导");
    ErrorCode ROLE_SCOPE_REQUIRED = new ErrorCode(1_900_012_142, "请管理员在编导节点配置发起人自选及角色范围");
    ErrorCode REVIEWER_UNAVAILABLE = new ErrorCode(1_900_012_143, "所选编导不可用、不在角色范围或缺少读取及审核权限，请重新选择；不能选择本人");
    ErrorCode REVIEW_READ_EXPIRED = new ErrorCode(1_900_012_144, "本次审批的学员查看权限已失效或资料不在本次范围内，请返回审批详情");
}
