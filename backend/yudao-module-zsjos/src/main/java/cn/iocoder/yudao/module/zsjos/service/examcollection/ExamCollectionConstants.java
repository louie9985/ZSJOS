package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import java.util.Set;

public interface ExamCollectionConstants {
    String PREFIX = "zsjos:exam-collection:";
    String QUERY = PREFIX + "query", CREATE = PREFIX + "create", NOTIFY = PREFIX + "notify";
    String RESOLVE = PREFIX + "resolve", COMPLETE = PREFIX + "complete", REOPEN = PREFIX + "reopen";
    String EXPORT = PREFIX + "export", MEMBERS = PREFIX + "members";
    String TASK_TYPE = "EXAM_MATERIAL_COLLECTION", BIZ_TYPE = "exam_material_collection", TASK_ACTION = "EXAM_MATERIAL_COLLECTION";
    String OPEN = "OPEN", COMPLETED = "COMPLETED", DRAFT = "DRAFT", SUBMITTED = "SUBMITTED";
    String ARCHIVED = "ARCHIVED", EXCEPTION = "EXCEPTION", INVALID = "INVALID";
    Set<String> ACTIONS = Set.of("query", "create", "notify", "resolve", "complete", "reopen", "export", "members");
    ErrorCode NOT_FOUND = new ErrorCode(1_900_170_001, "考试资料收集任务不存在或已删除");
    ErrorCode DENIED = new ErrorCode(1_900_170_002, "无权查看或操作考试资料收集任务");
    ErrorCode CLOSED = new ErrorCode(1_900_170_003, "考试资料收集已结束");
    ErrorCode CONFLICT = new ErrorCode(1_900_170_004, "收集任务已变化，请刷新后重试");
    ErrorCode SESSION_INVALID = new ErrorCode(1_900_170_005, "填写会话已失效，请重新打开收集链接");
    ErrorCode KEY_MISSING = new ErrorCode(1_900_170_006, "考试资料收集加密配置不可用，请联系管理员");
    ErrorCode CLASS_INVALID = new ErrorCode(1_900_170_007, "所选班级不存在或不在服务中，请重新选择");
}
