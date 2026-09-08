package cn.iocoder.yudao.module.zsjos.service.product;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Call inside a READ_COMMITTED transaction, after any product lock and before SKU writes. */
@Component
public class ProductCategoryLocks {
    @Resource private ZsjosProductCategoryMapper mapper;

    public Map<Long, ZsjosProductCategoryDO> paths(Collection<Long> leaves) {
        return lock(leaves, null);
    }

    public Map<Long, ZsjosProductCategoryDO> mutation(Long id, Long parentId) {
        List<Long> leaves = new ArrayList<>();
        if (id != null) leaves.add(id);
        if (parentId != null && parentId != 0) leaves.add(parentId);
        return lock(leaves, id);
    }

    private Map<Long, ZsjosProductCategoryDO> lock(Collection<Long> leaves, Long subtreeRoot) {
        Map<Long, ZsjosProductCategoryDO> before = index(mapper.selectCurrentList());
        Set<Long> ids = affected(before, leaves, subtreeRoot);
        Map<Long, ZsjosProductCategoryDO> locked = new HashMap<>();
        for (Long id : new TreeSet<>(ids)) {
            var row = mapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
            if (row == null || !Objects.equals(row.getParentId(), before.get(id).getParentId())) {
                throw exception(PRODUCT_CATALOG_CHANGED);
            }
            locked.put(id, row);
        }
        // A concurrent insert/move can change the discovered subtree before its parent is locked.
        var after = index(mapper.selectCurrentList());
        if (!ids.equals(affected(after, leaves, subtreeRoot))) throw exception(PRODUCT_CATALOG_CHANGED);
        return locked;
    }

    private Set<Long> affected(Map<Long, ZsjosProductCategoryDO> all, Collection<Long> leaves, Long root) {
        Set<Long> ids = new HashSet<>();
        for (Long leaf : leaves) {
            Long id = leaf;
            Set<Long> visited = new HashSet<>();
            while (id != null && id != 0) {
                var row = all.get(id);
                if (row == null) throw exception(PRODUCT_CATEGORY_NOT_EXISTS);
                if (!visited.add(id) || visited.size() > 10) throw exception(PRODUCT_CATEGORY_LEVEL_INVALID);
                ids.add(id); id = row.getParentId();
            }
        }
        if (root != null) {
            Set<Long> descendants = new HashSet<>(Set.of(root));
            boolean changed;
            do {
                changed = false;
                for (var row : all.values()) {
                    if (descendants.contains(row.getParentId())) changed |= descendants.add(row.getId());
                }
            } while (changed);
            ids.addAll(descendants);
        }
        return ids;
    }

    private Map<Long, ZsjosProductCategoryDO> index(List<ZsjosProductCategoryDO> rows) {
        Map<Long, ZsjosProductCategoryDO> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getId(), row));
        return result;
    }
}
