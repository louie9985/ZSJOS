package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Sales membership and persisted ownership are cumulative boundaries, even for tenant-wide readers. */
@Service
public class SubordinateSalesScope {
    @Resource private AdminUserApi users;
    @Resource private PostApi posts;
    @Resource private PermissionApi permissions;
    @Resource private LeadObjectPermissionService managers;
    @Resource private LeadTeamTransferPolicy ownership;

    public List<AdminUserRespDTO> readable(Long reader) {
        if (!permissions.hasTenantReadAllAccess(reader)) return managed(reader);
        var all = new ArrayList<>(users.getUserListByStatus(0));
        all.addAll(users.getUserListByStatus(1));
        return sales(all);
    }
    public List<AdminUserRespDTO> managed(Long manager) {
        var ids = managers.getManagedUserIds(manager);
        return ids.isEmpty() ? List.of() : sales(users.getUserList(ids));
    }
    private List<AdminUserRespDTO> sales(List<AdminUserRespDTO> candidates) {
        var post = posts.getPostByCode(SALES_POST_CODE);
        return post == null ? List.of() : candidates.stream()
                .filter(u -> u.getPostIds() != null && u.getPostIds().contains(post.getId())).toList();
    }
    public boolean salesLead(LeadDO lead) {
        return lead != null && OWNER_SALES.equals(ownership.resolveIdentity(lead));
    }
    public void requireManagedLead(LeadDO lead, Long manager) {
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        Long owner = lead.getOwnerUserId() == null ? lead.getRecycleSourceOwnerUserId() : lead.getOwnerUserId();
        if (!salesLead(lead) || managed(manager).stream().noneMatch(user -> Objects.equals(user.getId(), owner)))
            throw exception(SUBORDINATE_LEAD_OWNER_CHANGED);
    }
}
