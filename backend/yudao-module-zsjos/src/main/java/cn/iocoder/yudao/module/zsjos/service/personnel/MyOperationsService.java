package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadAssignmentService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.MyOperationsMemberRespVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PROVIDER_OWNER_SYSTEM_USER;

@Service
public class MyOperationsService {
    public static final String QUERY_PERMISSION = "zsjos:my-operations:query";
    public static final String RELATION_SCENE = "content_director_operator";
    @Resource private LeadAssignmentService leadAssignmentService;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private LeadMapper leadMapper;

    public boolean canReadMember(Long viewer, Long member) {
        return viewer != null && member != null && permissionApi.hasAnyPermissions(viewer, QUERY_PERMISSION)
                && leadAssignmentService.getActiveTargetUserIds(RELATION_SCENE, viewer).contains(member);
    }
    public void checkMember(Long viewer, Long member) {
        if (!canReadMember(viewer, member)) throw exception(LEAD_PERMISSION_DENIED);
    }
    public boolean canReadLead(LeadDO lead, Long viewer) {
        // Provider ownership includes assisted entry; creator and Partner ownership do not define this scope.
        return lead != null && PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && lead.getPartnerId() == null && canReadMember(viewer, lead.getProviderOwnerId());
    }
    public void checkLead(Long viewer, Long member, Long id) {
        checkMember(viewer, member);
        LeadDO lead = leadMapper.selectById(id);
        if (!canReadLead(lead, viewer) || !Objects.equals(member, lead.getProviderOwnerId())) throw exception(LEAD_PERMISSION_DENIED);
    }
    public List<MyOperationsMemberRespVO> members(Long viewer) {
        if (viewer == null || !permissionApi.hasAnyPermissions(viewer, QUERY_PERMISSION)) throw exception(LEAD_PERMISSION_DENIED);
        return leadAssignmentService.getActiveTargetUserIds(RELATION_SCENE, viewer).stream().distinct().sorted()
                .map(adminUserApi::getUser).filter(Objects::nonNull).map(user -> {
                    MyOperationsMemberRespVO row = new MyOperationsMemberRespVO();
                    row.setUserId(user.getId()); row.setName(user.getNickname()); row.setAccountStatus(user.getStatus());
                    row.setLeadCount(leadMapper.selectCount(new LambdaQueryWrapper<LeadDO>()
                            .eq(LeadDO::getProviderOwnerType, PROVIDER_OWNER_SYSTEM_USER).eq(LeadDO::getProviderOwnerId, user.getId())
                            .isNull(LeadDO::getPartnerId)));
                    return row;
                }).toList();
    }
}
