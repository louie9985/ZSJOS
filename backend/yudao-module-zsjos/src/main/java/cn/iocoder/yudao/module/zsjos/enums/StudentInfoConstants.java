package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface StudentInfoConstants {
    String PREFIX = "zsjos:student-info-form:";
    String CREATE = PREFIX + "create", READ = PREFIX + "read", LINK = PREFIX + "link-read";
    String REGENERATE = PREFIX + "regenerate", REVOKE = PREFIX + "revoke";
    String SENSITIVE = PREFIX + "sensitive-read", EXPORT = PREFIX + "export";
    String TAB = "student-info", ACTION = "GENERATE_STUDENT_INFO_FORM";
    ErrorCode CONFIG_INVALID = new ErrorCode(1_900_090_001, "收集表配置无效，请检查字段及字典设置");
    ErrorCode CONFIG_MISSING = new ErrorCode(1_900_090_002, "尚未发布学员信息收集表配置");
    ErrorCode VERSION_CONFLICT = new ErrorCode(1_900_090_003, "记录已变化，请刷新后重试");
    ErrorCode NOT_WON = new ErrorCode(1_900_090_004, "只有已成交客资允许收集学员信息");
    ErrorCode LINK_INVALID = new ErrorCode(1_900_090_005, "信息收集链接不存在");
    ErrorCode LINK_EXPIRED = new ErrorCode(1_900_090_006, "信息收集链接已过期或已撤销");
    ErrorCode ALREADY_SUBMITTED = new ErrorCode(1_900_090_007, "信息已提交，不可再次填写或生成");
    ErrorCode FIELD_INVALID = new ErrorCode(1_900_090_008, "字段不符合要求：{}");
    ErrorCode REFERENCE_INVALID = new ErrorCode(1_900_090_009, "字典或地区配置已变化，请重新选择：{}");
    ErrorCode URL_INVALID = new ErrorCode(1_900_090_010, "尚未配置有效的信息收集 H5 地址");
}
