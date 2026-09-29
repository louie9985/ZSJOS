package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrgMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MediaLeadOrgPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private MediaLeadOrgMapper mapper;
    @Resource private MediaLeadAccess access;

    @Override public String getBizType() { return "media-lead-org"; }

    @Override public boolean hasPermission(Long deptId, String action, Long userId) {
        if (!"unset".equals(action) || !access.has(MediaLeadAccess.TARGET_CONFIGURE)
                || !access.writeDeptAllowed(deptId)) return false;
        var row = mapper.selectOne(new LambdaQueryWrapperX<MediaLeadOrgDO>()
                .eq(MediaLeadOrgDO::getDeptId, deptId));
        return row != null && "CENTER".equals(row.getKind());
    }

    @Override public void check(Long deptId, String action, Long userId) {
        if (!hasPermission(deptId, action, userId)) throw MediaLeadAccess.denied();
    }
}
