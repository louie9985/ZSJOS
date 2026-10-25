package cn.iocoder.yudao.module.zsjos.service.zsjkqt;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
public interface ZsjkqtErrors {
    String ACCESS = "zsjos:zsjkqt:access";
    ErrorCode NOT_FOUND = new ErrorCode(1_091_050_000, "考勤草稿不存在或已删除");
    ErrorCode CONFLICT = new ErrorCode(1_091_050_001, "草稿已被其他人修改，请重新加载或另存副本");
    ErrorCode INVALID = new ErrorCode(1_091_050_002, "考勤数据格式不正确：{}");
    ErrorCode EXCEL = new ErrorCode(1_091_050_003, "Excel 处理失败：{}");
    ErrorCode DENIED = new ErrorCode(1_091_050_004, "没有中世健考勤通访问权限");
    ErrorCode CONFIG_CONFLICT = new ErrorCode(1_091_050_005, "部门时段已被其他人修改，请重新加载后重试");
}
