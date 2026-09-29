package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadTargetMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class MediaLeadTargetPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private MediaLeadTargetMapper mapper;
    @Resource private MediaLeadAccess access;

    @Override public String getBizType() { return "media-lead-target"; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        try {
            var row = mapper.selectById(id);
            if (row == null) return false;
            Long deptId = row.getDeptId();
            if (deptId == null) {
                deptId = "USER".equals(row.getScopeType()) && access.user(row.getScopeId()) != null
                        ? access.user(row.getScopeId()).getDeptId() : row.getScopeId();
            }
            if (!access.deptAllowed(deptId)) return false;
            access.authorize(new MediaLeadVO.Query(row.getScopeType(), row.getScopeId(), row.getPeriodStart(), row.getPeriodStart()), true);
            return "read".equals(action) || "update".equals(action)
                    && access.has(MediaLeadAccess.TARGET_UPDATE) && access.writeDeptAllowed(deptId);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            return false;
        }
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw MediaLeadAccess.denied();
    }
}
