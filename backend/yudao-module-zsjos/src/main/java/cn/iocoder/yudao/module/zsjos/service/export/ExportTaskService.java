package cn.iocoder.yudao.module.zsjos.service.export;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.export.vo.ExportTaskRespVO;

public interface ExportTaskService {
    Long create(Long userId, String exportType, String filterJson);
    PageResult<ExportTaskRespVO> getMyPage(Long userId, PageParam page, String exportType);
    void cancel(Long userId, Long taskId);
    String getDownloadUrl(Long userId, Long taskId);
    int processAvailable();
    int expireFiles();
    int cleanInactiveTasks();
}
