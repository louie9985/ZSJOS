package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.MATERIAL_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_PERMISSION_DENIED;

@Component
public class MaterialObjectPermissionProvider implements ZsjosObjectPermissionProvider {

    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private PermissionApi permissionApi;

    @Override
    public String getBizType() {
        return "material";
    }

    @Override
    public boolean hasPermission(Long materialId, String action, Long userId) {
        MaterialDO material = materialMapper.selectById(materialId);
        if (material == null || userId == null) {
            return false;
        }
        boolean owner = Objects.equals(material.getOwnerUserId(), userId);
        boolean manager = permissionApi.hasAnyPermissions(userId, "zsjos:material:manage");
        return switch (action) {
            case "read" -> owner || manager || material.getCurrentEffectiveVersionId() != null
                    && permissionApi.hasAnyPermissions(userId, "zsjos:material:query");
            case "edit", "submit" -> owner || manager;
            case "disable", "restore" -> manager;
            case "interact", "reference" -> MATERIAL_EFFECTIVE.equals(material.getStatus())
                    && material.getCurrentEffectiveVersionId() != null;
            default -> false;
        };
    }

    @Override
    public void check(Long materialId, String action, Long userId) {
        if (!hasPermission(materialId, action, userId)) {
            throw exception(MATERIAL_PERMISSION_DENIED);
        }
    }
}
