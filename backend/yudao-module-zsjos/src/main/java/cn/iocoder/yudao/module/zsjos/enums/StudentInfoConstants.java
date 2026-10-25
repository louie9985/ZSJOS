package cn.iocoder.yudao.module.zsjos.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface StudentInfoConstants {
    int ATTACHMENT_MAX_SIZE_MB = 100;
    String PREFIX = "zsjos:student-info-form:";
    String CREATE = PREFIX + "create", READ = PREFIX + "read", LINK = PREFIX + "link-read";
    String REGENERATE = PREFIX + "regenerate", REVOKE = PREFIX + "revoke";
    String SENSITIVE = PREFIX + "sensitive-read", EXPORT = PREFIX + "export";
    String TAB = "student-info", ACTION = "GENERATE_STUDENT_INFO_FORM";
    String ARCHIVE_READ = "zsjos:student-archive:read", ARCHIVE_UPDATE = "zsjos:student-archive:update";
    // Archive access must not require granting the registration pool parent menu.
    String ARCHIVE_SENSITIVE = "zsjos:student-archive:sensitive-read", ARCHIVE_EXPORT = "zsjos:student-archive:export";
    // Exam staff permissions are the server-owned role boundary for hidden exam fields.
    String EXAM_READ = "zsjos:exam-calendar:query", EXAM_VISIBILITY_UPDATE = "zsjos:exam-calendar:manage";
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
    ErrorCode DRAFT_CONFLICT = new ErrorCode(1_900_090_011, "草稿已在其他页面更新，请保留当前内容后重新打开链接");
    ErrorCode ATTACHMENT_EMPTY = new ErrorCode(1_900_090_012, "文件为空，请保存有效内容后重新上传");
    ErrorCode ATTACHMENT_TOO_LARGE = new ErrorCode(1_900_090_013, "单个附件不能超过 {}MB");
}
