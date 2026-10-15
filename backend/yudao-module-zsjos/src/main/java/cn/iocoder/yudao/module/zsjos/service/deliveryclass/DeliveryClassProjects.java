package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassProjectRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import java.util.*;
import java.util.stream.Collectors;

final class DeliveryClassProjects {
    private DeliveryClassProjects() {}
    static DeliveryClassProjectRespVO parse(String snapshot) {
        Map<?, ?> data = JsonUtils.parseObjectQuietly(snapshot, Map.class);
        if (data == null) return null;
        Object value = data.get("categoryId");
        if (!(value instanceof Number) && !(value instanceof String)) return null;
        final long id;
        try { id = Long.parseLong(value.toString()); } catch (NumberFormatException ignored) { return null; }
        if (id <= 0) return null;
        String name = data.get("categoryName") instanceof String label && !label.isBlank() ? label : "项目名称未记录";
        List<String> names = new ArrayList<>();
        if (data.get("categoryPath") instanceof List<?> path) {
            for (Object node : path) if (node instanceof Map<?, ?> item && item.get("name") instanceof String label
                    && !label.isBlank()) names.add(label);
        }
        return new DeliveryClassProjectRespVO(id, name, names.isEmpty() ? name : String.join(" / ", names));
    }
    static Map<Long, List<DeliveryClassProjectRespVO>> load(DeliveryClassMapper mapper, List<DeliveryClassDO> rows) {
        Map<Long, Map<Long, DeliveryClassProjectRespVO>> projects = new HashMap<>();
        Map<Long, List<DeliveryClassDO>> tenants = rows.stream().filter(row -> !Boolean.TRUE.equals(row.getSystemClass()))
                .collect(Collectors.groupingBy(DeliveryClassDO::getTenantId));
        tenants.forEach((tenant, classes) -> {
            // Bound each IN list without truncating the candidate population to a page.
            for (int offset = 0; offset < classes.size(); offset += 500) {
                List<Long> ids = classes.subList(offset, Math.min(offset + 500, classes.size())).stream().map(DeliveryClassDO::getId).toList();
                for (Map<String, Object> item : mapper.selectProjectSnapshots(tenant, ids)) {
                    DeliveryClassProjectRespVO project = parse((String) item.get("snapshot"));
                    if (project != null) projects.computeIfAbsent(((Number) item.get("classId")).longValue(), key -> new TreeMap<>())
                            .putIfAbsent(project.id(), project);
                }
            }
        });
        Map<Long, List<DeliveryClassProjectRespVO>> result = new HashMap<>();
        projects.forEach((id, values) -> result.put(id, List.copyOf(values.values())));
        return result;
    }
}
