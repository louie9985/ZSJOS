package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;

public interface ExportTypeProvider {
    String getType();
    String getCreatePermission();
    void validateFilter(String filterJson);
    ExportResult generate(ExportTaskDO task) throws Exception;

    record ExportResult(byte[] content, String fileName, long rowCount) {}
}
