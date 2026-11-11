package cn.iocoder.yudao.module.zsjos.service.versionupdate;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public final class VersionUpdateConstants {
    private VersionUpdateConstants() {}
    public static final String PREFIX = "zsjos:version-update:";
    public static final String QUERY = PREFIX + "query";
    public static final String READ = PREFIX + "read";
    public static final String ACKNOWLEDGE = PREFIX + "acknowledge";
    public static final ErrorCode NOT_FOUND = new ErrorCode(1_900_080_001, "版本更新通知不存在");
    public static final ErrorCode DENIED = new ErrorCode(1_900_080_002, "无权访问此版本更新通知");
    public static final ErrorCode STATE = new ErrorCode(1_900_080_003, "版本更新通知状态已变化，请重新打开");
    public static final ErrorCode RECIPIENT = new ErrorCode(1_900_080_004, "接收人员已停用或缺少更新记录及知悉权限，请重新选择");
    public static final ErrorCode FILE = new ErrorCode(1_900_080_005, "附件无效或无权使用该附件");
    public static final ErrorCode CONTENT = new ErrorCode(1_900_080_006, "请填写版本更新正文");
}
