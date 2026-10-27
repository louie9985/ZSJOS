package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServiceAnnotationVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceAnnotationConstants.*;

@Service
public class StudentServiceAnnotationService {
    @Resource private StudentServiceAnnotationMapper annotations;
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentServiceAnnotationPermissionProvider permissions;
    @Resource private DictDataApi dictionaries;

    @ZsjosPermission(bizType="student-service-annotation", bizId="#relationId", action="read")
    public Detail detail(Long relationId) {
        return project(relationId, annotations.byRelation(relationId));
    }

    @ZsjosPermission(bizType="student-service-annotation", bizId="#relationId", action="read")
    public List<Option> options(Long relationId) {
        return dictionaries.getDictDataList(DICT).stream()
                .filter(item -> Integer.valueOf(0).equals(item.getStatus()))
                .filter(item -> "true".equals(item.getValue()) || "false".equals(item.getValue()))
                .map(item -> new Option(item.getValue(), item.getLabel())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType="student-service-annotation", bizId="#relationId", action="update")
    public Detail save(Long relationId, Save req) {
        // Lock the owning relation even for the first annotation, and recheck assignment after waiting.
        relations.selectOne(new LambdaQueryWrapperX<ServiceRelationDO>().eq(ServiceRelationDO::getId, relationId).last("FOR UPDATE"));
        permissions.check(relationId, "update", getLoginUserId());
        var existing = annotations.byRelation(relationId);
        int version = existing == null ? 0 : existing.getVersion();
        if (!Objects.equals(req.getVersion(), version)) throw exception(CONFLICT);
        var row = new StudentServiceAnnotationDO();
        row.setServiceRelationId(relationId);
        row.setProxyStudent(req.getProxyStudent());
        row.setProxyStudentLabel(label(req.getProxyStudent(), existing == null ? null : existing.getProxyStudent(), existing == null ? null : existing.getProxyStudentLabel()));
        row.setServiceNeeded(req.getServiceNeeded());
        row.setServiceNeededLabel(label(req.getServiceNeeded(), existing == null ? null : existing.getServiceNeeded(), existing == null ? null : existing.getServiceNeededLabel()));
        String agent = req.getAgentName() == null ? "" : req.getAgentName().trim();
        if ("true".equals(req.getProxyStudent()) && (agent.isEmpty() || agent.length() > 100)) throw exception(AGENT_REQUIRED);
        row.setAgentName("true".equals(req.getProxyStudent()) ? agent : null);
        row.setVersion(version + 1);
        if (existing == null) annotations.insert(row);
        else {
            row.setId(existing.getId()); row.setUpdater(String.valueOf(getLoginUserId())); row.setUpdateTime(LocalDateTime.now());
            if (annotations.replace(row, version) != 1) throw exception(CONFLICT);
        }
        return project(relationId, row);
    }

    private String label(String value, String previous, String snapshot) {
        if (value == null) return null;
        if (!"true".equals(value) && !"false".equals(value)) throw exception(INVALID);
        if (Objects.equals(value, previous)) return snapshot;
        return dictionaries.getDictDataList(DICT).stream()
                .filter(item -> Integer.valueOf(0).equals(item.getStatus()) && value.equals(item.getValue()))
                .map(item -> item.getLabel()).findFirst().orElseThrow(() -> exception(INVALID));
    }
    private Detail project(Long relationId, StudentServiceAnnotationDO row) {
        var result = new Detail(); result.setServiceRelationId(relationId);
        result.setCanEdit(permissions.hasPermission(relationId, "update", getLoginUserId()));
        if (row != null) {
            result.setVersion(row.getVersion()); result.setProxyStudent(row.getProxyStudent());
            result.setProxyStudentLabel(row.getProxyStudentLabel()); result.setAgentName(row.getAgentName());
            result.setServiceNeeded(row.getServiceNeeded()); result.setServiceNeededLabel(row.getServiceNeededLabel());
        }
        return result;
    }
}
