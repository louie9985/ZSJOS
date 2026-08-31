package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningExecCardDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningExecCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.Objects;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_POSITIONING_EXEC_CARD;

@Component
public class PositioningExecCardObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private PositioningExecCardMapper mapper;
    @Resource private PositioningCardMapper cardMapper;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private PermissionApi permissionApi;
    public String getBizType() { return BIZ_TYPE_POSITIONING_EXEC_CARD; }
    public boolean hasPermission(Long id, String action, Long userId) {
        PositioningExecCardDO card = mapper.selectById(id);
        if (card == null) return false;
        var positioning = cardMapper.selectById(card.getPositioningCardId());
        var account = accountMapper.selectById(card.getAccountId());
        if (positioning == null || account == null) return false;
        if ("read".equals(action) && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:query-all")) return true;
        return Objects.equals(userId, positioning.getDirectorUserId()) || Objects.equals(userId, account.getOwnerOperatorUserId());
    }
}
