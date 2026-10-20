package cn.iocoder.yudao.module.zsjos.service.educationteam;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
/** Preserve existing client error identities while giving education its own domain wording. */
final class EducationTeamErrors {
 private EducationTeamErrors() {}
 static final ErrorCode NOT_MANAGED=new ErrorCode(1_900_003_053,"该教务人员不在当前主管管理范围内");
 static final ErrorCode STATUS_INVALID=new ErrorCode(1_900_003_055,"教务账号状态无效");
 static final ErrorCode TARGET_INVALID=new ErrorCode(1_900_003_056,"目标教务人员无效、已停用或不在管理范围内");
}
