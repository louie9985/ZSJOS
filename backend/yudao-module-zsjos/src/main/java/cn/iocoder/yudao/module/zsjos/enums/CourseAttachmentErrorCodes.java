package cn.iocoder.yudao.module.zsjos.enums;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
public interface CourseAttachmentErrorCodes {
 ErrorCode INVALID_FILE = new ErrorCode(1_900_097_001,"请上传100MB以内的图片、PDF、Word、Excel或PPT文件");
 ErrorCode INVALID_REFERENCE = new ErrorCode(1_900_097_002,"课程附件不存在或无权引用，请移除后重新上传");
 ErrorCode TOO_MANY_FILES = new ErrorCode(1_900_097_003,"课程最多支持20个附件，且不能重复");
}
