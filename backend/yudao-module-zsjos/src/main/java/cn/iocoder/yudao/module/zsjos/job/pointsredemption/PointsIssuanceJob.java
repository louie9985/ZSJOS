package cn.iocoder.yudao.module.zsjos.job.pointsredemption;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.zsjos.service.pointsredemption.PointsIssuanceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
@Component public class PointsIssuanceJob implements JobHandler {
    @Resource private PointsIssuanceService service;
    @Override @TenantJob public String execute(String param) { return "积分发码处理：" + service.drain(); }
}
