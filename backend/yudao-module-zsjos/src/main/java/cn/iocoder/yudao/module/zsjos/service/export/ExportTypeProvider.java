package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;

public interface ExportTypeProvider {
    String getType();
    String getCreatePermission();
    default void checkCreator(Long userId) {}
    void validateFilter(String filterJson);
    ExportResult generate(ExportTaskDO task) throws Exception;

    default boolean privateResult() { return false; }
    default void checkDownload(ExportTaskDO task) {}
    default byte[] encodeResult(byte[] content) throws Exception { return content; }
    default byte[] decodeResult(byte[] content) throws Exception { return content; }
    default String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }

    record ExportResult(byte[] content, String fileName, long rowCount) {}
}
