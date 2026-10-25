package cn.iocoder.yudao.module.zsjos.service.gift;
import cn.iocoder.yudao.module.zsjos.controller.admin.gift.vo.GiftConfigSaveReqVO; import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO; import java.util.*;
public interface GiftConfigService { Long create(GiftConfigSaveReqVO r); void update(GiftConfigSaveReqVO r); void delete(Long id); List<GiftConfigDO> list(Integer status);
    default List<GiftConfigDO> listFlat(Integer status) {
        List<GiftConfigDO> result = new ArrayList<>();
        Deque<GiftConfigDO> pending = new ArrayDeque<>(list(status));
        Set<Long> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            GiftConfigDO row = pending.removeFirst();
            if (!visited.add(row.getId())) continue;
            result.add(row);
            if (row.getChildren() != null) pending.addAll(row.getChildren());
        }
        return result;
    }
}
