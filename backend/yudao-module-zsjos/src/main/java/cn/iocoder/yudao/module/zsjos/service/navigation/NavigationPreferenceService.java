package cn.iocoder.yudao.module.zsjos.service.navigation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.navigation.vo.NavigationPreferenceVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.navigation.NavigationPreferenceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
public class NavigationPreferenceService {
    @Resource private NavigationPreferenceMapper mapper;

    public NavigationPreferenceVO get(String client) {
        validateClient(client);
        var value = mapper.selectPreference(currentUser(), client);
        return value == null ? new NavigationPreferenceVO()
                : JsonUtils.parseObject(value.getPreferenceJson(), NavigationPreferenceVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(String client, NavigationPreferenceVO preference) {
        validateClient(client);
        Long userId = currentUser();
        validate(preference);
        // Preferences are inert keys, never menus or grants. Consumers intersect them with
        // the current authorized siblings; removed/reparented keys cannot expose a route.
        mapper.savePreference(TenantContextHolder.getRequiredTenantId(), userId, client,
                JsonUtils.toJsonString(preference));
    }

    static void validate(NavigationPreferenceVO preference) {
        if (preference == null || preference.getGroups() == null || preference.getGroups().size() > 300) {
            throw exception(BAD_REQUEST);
        }
        Set<String> parents = new HashSet<>();
        int total = 0;
        for (var group : preference.getGroups()) {
            if (group == null || !validKey(group.getParentKey(), 300) || !parents.add(group.getParentKey())
                    || group.getKeys() == null || group.getKeys().size() > 500) throw exception(BAD_REQUEST);
            Set<String> keys = new HashSet<>();
            for (String key : group.getKeys()) {
                if (!validKey(key, 160) || !keys.add(key)) throw exception(BAD_REQUEST);
            }
            total += keys.size();
        }
        if (total > 3000 || JsonUtils.toJsonString(preference).length() > 100000) throw exception(BAD_REQUEST);
    }

    private static boolean validKey(String key, int max) {
        return key != null && !key.isBlank() && key.length() <= max && key.chars().noneMatch(Character::isISOControl);
    }
    private static void validateClient(String client) {
        if (!Set.of("admin", "workbench").contains(client == null ? "" : client)) throw exception(BAD_REQUEST);
    }
    private static Long currentUser() {
        Long userId = getLoginUserId();
        if (userId == null) throw exception(UNAUTHORIZED);
        return userId;
    }
}
