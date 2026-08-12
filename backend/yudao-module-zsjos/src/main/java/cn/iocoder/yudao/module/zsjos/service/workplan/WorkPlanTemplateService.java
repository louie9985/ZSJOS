package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;

import java.util.List;

public interface WorkPlanTemplateService {
    List<WorkPlanTypeRespVO> getTypes();
    Long createType(WorkPlanTypeSaveReqVO reqVO);
    void updateType(Long id, WorkPlanTypeSaveReqVO reqVO);
    List<WorkPlanTemplateRespVO> getTemplates(Long typeId);
    List<WorkPlanTemplateRespVO> getAvailableTemplates(Long userId);
    WorkPlanTemplateRespVO getTemplate(Long id);
    Long createTemplate(WorkPlanTemplateSaveReqVO reqVO, Long userId);
    void updateTemplate(Long id, WorkPlanTemplateSaveReqVO reqVO, Long userId);
    Long copyTemplateVersion(Long id, Long userId);
    void publishTemplate(Long id, Long userId);
    void disableTemplate(Long id, Long userId);
}
