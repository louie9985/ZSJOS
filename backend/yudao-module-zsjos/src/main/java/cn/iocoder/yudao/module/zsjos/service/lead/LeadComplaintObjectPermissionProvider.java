package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadComplaintMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class LeadComplaintObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private LeadComplaintMapper mapper;
    @Resource @org.springframework.context.annotation.Lazy private LeadComplaintService service;
    @Override public String getBizType() { return "lead-complaint"; }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
    }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        if (id == null || userId == null || !"handle".equals(action)) return false;
        var row = mapper.selectById(id);
        if (row == null) return false;
        try { service.checkManagedComplaint(row, userId); return true; }
        catch (cn.iocoder.yudao.framework.common.exception.ServiceException ex) { return false; }
    }
}
