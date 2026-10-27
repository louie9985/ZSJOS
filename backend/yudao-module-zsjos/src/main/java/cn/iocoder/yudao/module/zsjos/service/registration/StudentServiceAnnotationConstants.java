package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface StudentServiceAnnotationConstants {
    String READ = "zsjos:student-service-annotation:read";
    String UPDATE = "zsjos:student-service-annotation:update";
    String DICT = "infra_boolean_string";
    ErrorCode CONFLICT = new ErrorCode(1_900_094_001, "批注已被其他人员修改，请读取最新内容后重新填写");
    ErrorCode INVALID = new ErrorCode(1_900_094_002, "批注选项无效或已停用，请重新选择");
    ErrorCode AGENT_REQUIRED = new ErrorCode(1_900_094_003, "代理学员请填写代理人（最多100字）");
}
