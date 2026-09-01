package cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkbenchLayoutMapper extends BaseMapperX<WorkbenchLayoutDO> {

    default WorkbenchLayoutDO selectByScope(String scopeType, Long scopeId) {
        return selectOne(WorkbenchLayoutDO::getScopeType, scopeType,
                WorkbenchLayoutDO::getScopeId, scopeId);
    }

    default List<WorkbenchLayoutDO> selectListByScopeType(String scopeType) {
        return selectList(WorkbenchLayoutDO::getScopeType, scopeType);
    }

    default List<WorkbenchLayoutDO> selectPublishedRoleLayouts(Collection<Long> roleIds) {
        return selectList(new LambdaQueryWrapperX<WorkbenchLayoutDO>()
                .eq(WorkbenchLayoutDO::getScopeType, "ROLE")
                .inIfPresent(WorkbenchLayoutDO::getScopeId, roleIds)
                .isNotNull(WorkbenchLayoutDO::getPublishedVersionId)
                .eq(WorkbenchLayoutDO::getPublishedEnabled, true)
                .orderByAsc(WorkbenchLayoutDO::getPublishedPriority));
    }

    default int updateDraft(Long id, Integer expectedRevision, String snapshotJson,
                            Long restoredFromVersionId) {
        return update(new WorkbenchLayoutDO()
                        .setDraftSnapshotJson(snapshotJson)
                        .setDraftRevision(expectedRevision + 1)
                        .setDraftRestoredFromVersionId(restoredFromVersionId),
                new LambdaUpdateWrapper<WorkbenchLayoutDO>()
                        .eq(WorkbenchLayoutDO::getId, id)
                        .eq(WorkbenchLayoutDO::getDraftRevision, expectedRevision));
    }

    default int updatePublished(Long id, Integer expectedRevision, Long versionId, Integer versionNo,
                                Boolean enabled, Integer priority) {
        return update(null, new LambdaUpdateWrapper<WorkbenchLayoutDO>()
                        .eq(WorkbenchLayoutDO::getId, id)
                        .eq(WorkbenchLayoutDO::getDraftRevision, expectedRevision)
                        .set(WorkbenchLayoutDO::getDraftRevision, expectedRevision + 1)
                        .set(WorkbenchLayoutDO::getDraftRestoredFromVersionId, null)
                        .set(WorkbenchLayoutDO::getPublishedVersionId, versionId)
                        .set(WorkbenchLayoutDO::getPublishedVersionNo, versionNo)
                        .set(WorkbenchLayoutDO::getPublishedEnabled, enabled)
                        .set(WorkbenchLayoutDO::getPublishedPriority, priority));
    }

    @Select("SELECT * FROM system_workbench_layout WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    WorkbenchLayoutDO selectByIdForUpdate(@Param("id") Long id);

}
