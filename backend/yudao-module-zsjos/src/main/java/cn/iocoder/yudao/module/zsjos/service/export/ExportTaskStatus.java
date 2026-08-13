package cn.iocoder.yudao.module.zsjos.service.export;

public interface ExportTaskStatus {
    String QUEUED = "queued";
    String PRECHECKING = "prechecking";
    String GENERATING = "generating";
    String READY = "ready";
    String FAILED = "failed";
    String CANCELLED = "cancelled";
    String EXPIRED = "expired";
}
