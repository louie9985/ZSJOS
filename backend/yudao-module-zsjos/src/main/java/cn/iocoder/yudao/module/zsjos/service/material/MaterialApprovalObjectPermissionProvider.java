package cn.iocoder.yudao.module.zsjos.service.material;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialApprovalRoundMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialApprovalRoundDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
@Component
public class MaterialApprovalObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private BpmProcessTaskApi taskApi;
    @Resource private MaterialApprovalRoundMapper roundMapper;
    @Resource private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;
    public String getBizType() { return "material-approval"; }
    public boolean hasPermission(Long versionId, String action, Long userId) {
        if (userId == null || versionId == null || !List.of("read", "decide").contains(action)) return false;
        var rounds = roundMapper.selectList(MaterialApprovalRoundDO::getMaterialVersionId, versionId);
        if ("read".equals(action) && !rounds.isEmpty() && permissionApi.hasTenantReadAllAccess(userId)) return true;
        for (MaterialApprovalRoundDO round : rounds) {
            BpmTaskPageReqDTO query = new BpmTaskPageReqDTO();
            query.setProcessDefinitionKey(round.getProcessDefinitionKey());
            query.setProcessInstanceIds(List.of(round.getProcessInstanceId()));
            query.setPageSize(1);
            if (taskApi.getTodoTaskPage(userId, query).getTotal() > 0) return true;
            if ("read".equals(action) && taskApi.getDoneTaskPage(userId, query).getTotal() > 0) return true;
        }
        return false;
    }
    public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(MaterialApprovalErrors.INVALID_TASK);
    }
}
