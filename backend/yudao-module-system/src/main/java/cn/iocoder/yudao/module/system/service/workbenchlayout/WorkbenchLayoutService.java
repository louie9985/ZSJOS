package cn.iocoder.yudao.module.system.service.workbenchlayout;

import cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.service.workbenchlayout.model.WorkbenchMenuProjection;

import java.util.List;
import java.util.Set;

public interface WorkbenchLayoutService {

    WorkbenchLayoutCandidateRespVO getCandidates();

    WorkbenchLayoutDraftRespVO getDraft(String scopeType, Long scopeId);

    Integer saveDraft(WorkbenchLayoutSaveReqVO reqVO);

    WorkbenchLayoutPreviewRespVO preview(WorkbenchLayoutPreviewReqVO reqVO);

    WorkbenchLayoutImpactRespVO getPublishImpact(String scopeType, Long scopeId);

    Long publish(WorkbenchLayoutPublishReqVO reqVO, Long publisherUserId);

    List<WorkbenchLayoutVersionRespVO> getVersions(String scopeType, Long scopeId);

    Integer restoreDraft(WorkbenchLayoutRestoreReqVO reqVO);

    WorkbenchMenuProjection getProjection(Set<Long> roleIds, List<MenuDO> authorizedMenus);

}
