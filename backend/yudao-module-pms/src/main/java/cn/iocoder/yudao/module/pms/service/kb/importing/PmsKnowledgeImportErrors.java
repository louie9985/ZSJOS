package cn.iocoder.yudao.module.pms.service.kb.importing;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
public interface PmsKnowledgeImportErrors {
    // PMS-owned import range; 1_045_090_xxx is already used by ZSJOS partner assignment.
    ErrorCode INVALID = new ErrorCode(1_053_290_000, "{}" );
    ErrorCode DENIED = new ErrorCode(1_053_290_001, "无权操作此导入任务或目标");
    ErrorCode TARGET = new ErrorCode(1_053_290_002, "目标不存在或不可写");
    ErrorCode BUSY = new ErrorCode(1_053_290_003, "文件正在处理中，请稍后继续");
    ErrorCode STORAGE = new ErrorCode(1_053_290_004, "文件存储失败，请重试");
    ErrorCode CLOSED = new ErrorCode(1_053_290_005, "导入任务已结束");
}
