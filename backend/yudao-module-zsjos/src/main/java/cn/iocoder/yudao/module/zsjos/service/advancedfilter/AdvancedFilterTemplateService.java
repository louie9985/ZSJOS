package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterTemplateSaveReqVO;

import java.util.List;

public interface AdvancedFilterTemplateService {
    List<AdvancedFilterTemplateRespVO> visibleList(String scene, String pageKey, Long userId);

    List<AdvancedFilterTemplateRespVO> systemList(String scene, String pageKey);

    Long createPersonal(AdvancedFilterTemplateSaveReqVO reqVO, Long userId);

    void updatePersonal(AdvancedFilterTemplateSaveReqVO reqVO, Long userId);

    void deletePersonal(Long id, Long userId);

    Long createSystem(AdvancedFilterTemplateSaveReqVO reqVO);

    void updateSystem(AdvancedFilterTemplateSaveReqVO reqVO);

    void deleteSystem(Long id);
}
