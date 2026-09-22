package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterCatalogRespVO.OptionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.ADVANCED_FILTER_INVALID;

/** System owns the organization tree; Lead queries still intersect their own object scope. */
@Service
public class LeadFilterOrganizationService {
    @Resource private DeptApi deptApi;
    @Resource private AdminUserApi adminUserApi;

    public List<OptionVO> options(List<OptionVO> visibleUsers) {
        if (visibleUsers.isEmpty()) return List.of();
        var userIds = visibleUsers.stream().map(option -> Long.valueOf(option.value())).toList();
        Set<Long> deptIds = new LinkedHashSet<>();
        adminUserApi.getUserList(userIds).stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).forEach(deptIds::add);
        Map<Long, DeptRespDTO> tree = new LinkedHashMap<>();
        for (var dept : deptApi.getDeptList(deptIds)) {
            tree.put(dept.getId(), dept);
            deptApi.getParentDeptList(dept.getId()).forEach(parent -> tree.put(parent.getId(), parent));
        }
        return tree.values().stream().map(dept -> new OptionVO(String.valueOf(dept.getId()), path(dept, tree)))
                .sorted(Comparator.comparing(OptionVO::label)).toList();
    }

    private String path(DeptRespDTO dept, Map<Long, DeptRespDTO> tree) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        while (dept != null && visited.add(dept.getId())) {
            names.addFirst(dept.getName());
            dept = tree.get(dept.getParentId());
        }
        return String.join(" / ", names);
    }

    public static Set<Long> requestedIds(Object raw) {
        if (!(raw instanceof Collection<?> values) || values.isEmpty()) throw exception(ADVANCED_FILTER_INVALID);
        Set<Long> requested = new LinkedHashSet<>();
        try {
            for (Object value : values) {
                long id = Long.parseLong(String.valueOf(value));
                if (id <= 0) throw new NumberFormatException();
                requested.add(id);
            }
        } catch (NumberFormatException ex) { throw exception(ADVANCED_FILTER_INVALID); }
        return requested;
    }

    public List<Long> ownerIds(Object raw) {
        Set<Long> requested = requestedIds(raw);
        // A removed saved selection is actionable; never silently broaden an exclusion query.
        Set<Long> existing = new LinkedHashSet<>();
        deptApi.getDeptList(requested).forEach(dept -> existing.add(dept.getId()));
        if (!existing.equals(requested)) throw exception(ADVANCED_FILTER_INVALID);
        Set<Long> expanded = new LinkedHashSet<>(existing);
        deptApi.getChildDeptList(existing).forEach(dept -> expanded.add(dept.getId()));
        return adminUserApi.getUserListByDeptIds(expanded).stream().map(AdminUserRespDTO::getId).distinct().toList();
    }
}
