package cn.iocoder.yudao.module.zsjos.job.studentinfo;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** Administrators may schedule bounded cleanup; never deletes submitted collection materials. */
@Component
public class StudentInfoImageCleanupJob implements JobHandler {
    @Resource private StudentInfoImageService images;
    @Override @TenantJob public String execute(String param) { return "清理过期未提交收集表图片："+images.cleanupExpired(); }
}
