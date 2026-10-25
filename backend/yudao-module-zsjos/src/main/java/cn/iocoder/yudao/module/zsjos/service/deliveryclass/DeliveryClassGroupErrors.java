package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface DeliveryClassGroupErrors {
    ErrorCode MISSING = new ErrorCode(1_091_140_001, "班级组不存在或已删除，请刷新");
    ErrorCode DENIED = new ErrorCode(1_091_140_002, "无权维护该班级组或班级");
    ErrorCode DEPARTMENT = new ErrorCode(1_091_140_003, "只能归入同部门的班级组");
    ErrorCode CONFLICT = new ErrorCode(1_091_140_004, "班级组或班级已变化，请刷新后重试");
    ErrorCode NOT_EMPTY = new ErrorCode(1_091_140_005, "班级组仍有小班，请先移出全部小班");
    ErrorCode INVALID = new ErrorCode(1_091_140_006, "归组参数无效，系统班不能手工归组");
}
