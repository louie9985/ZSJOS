package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.BIZ_TYPE_POSITIONING_CARD;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.POSITIONING_CARD_PERMISSION_DENIED;

@Component
public class PositioningCardObjectPermissionProvider implements ZsjosObjectPermissionProvider {
    private static final Set<String> DIRECTOR_ACTIONS = Set.of("read", "edit", "submit-review");
    private static final Set<String> OPERATOR_ACTIONS = Set.of("read", "operator-confirm", "operator-reject",
            "student-link-generate");
    @Resource private PositioningCardMapper mapper;
    @Resource private PermissionApi permissionApi;
    @Resource private MediaAccountMapper accountMapper;
    @Override public String getBizType() { return BIZ_TYPE_POSITIONING_CARD; }
    @Override public boolean hasPermission(Long id, String action, Long userId) {
        PositioningCardDO card = mapper.selectById(id);
        if (card == null) return false;
        if ("read".equals(action) && permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:query-all")) return true;
        if (userId.equals(card.getDirectorUserId()) && DIRECTOR_ACTIONS.contains(action)) return true;
        if (card.getServiceRelationId() != null) {
            return userId.equals(card.getOperatorUserId()) && OPERATOR_ACTIONS.contains(action);
        }
        MediaAccountDO account = accountMapper.selectById(card.getAccountId());
        return account != null && userId.equals(account.getOwnerOperatorUserId())
                && OPERATOR_ACTIONS.contains(action);
    }
    @Override public void check(Long id, String action, Long userId) {
        if (!hasPermission(id, action, userId)) throw exception(POSITIONING_CARD_PERMISSION_DENIED);
    }
}
