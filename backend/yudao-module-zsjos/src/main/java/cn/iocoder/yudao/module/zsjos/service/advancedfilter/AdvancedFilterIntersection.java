package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import java.util.List;

/** Server-only composition: clients still deserialize plain groups and retain their original limits. */
public final class AdvancedFilterIntersection extends AdvancedFilterGroupReqVO {
    public AdvancedFilterIntersection(List<AdvancedFilterGroupReqVO> groups) {
        if (groups.size() > 3 || groups.stream().anyMatch(g -> g instanceof AdvancedFilterIntersection))
            throw new IllegalArgumentException("Invalid server filter composition");
        setLogic("AND");
        setGroups(List.copyOf(groups));
    }
}
