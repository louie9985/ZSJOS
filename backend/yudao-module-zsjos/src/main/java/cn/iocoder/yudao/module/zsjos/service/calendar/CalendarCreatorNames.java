package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Current display names for actual audit creators, never inferred from owner or operator. */
@Service
public class CalendarCreatorNames {
    @Resource private AdminUserApi userApi;

    public Map<String, String> resolve(List<? extends BaseDO> rows) {
        Map<String, Long> identities = new HashMap<>();
        for (var row : rows) {
            String creator = row.getCreator();
            if (creator == null || !creator.matches("[1-9][0-9]*")) continue;
            try { identities.put(creator, Long.parseLong(creator)); }
            catch (NumberFormatException ignored) { /* Legacy non-user audit values have no display identity. */ }
        }
        if (identities.isEmpty()) return new HashMap<>();
        var users = userApi.getUserMap(new HashSet<>(identities.values()));
        Map<String, String> names = new HashMap<>();
        identities.forEach((creator, id) -> {
            var user = users.get(id);
            if (user != null && user.getNickname() != null) names.put(creator, user.getNickname());
        });
        return names;
    }
}
