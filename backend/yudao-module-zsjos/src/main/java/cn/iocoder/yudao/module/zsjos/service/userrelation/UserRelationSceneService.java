package cn.iocoder.yudao.module.zsjos.service.userrelation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationScenePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;

import java.util.List;

public interface UserRelationSceneService {

    Long createScene(UserRelationSceneSaveReqVO reqVO);

    void updateScene(UserRelationSceneSaveReqVO reqVO);

    void deleteScene(Long id);

    UserRelationSceneRespVO getScene(Long id);

    PageResult<UserRelationSceneRespVO> getScenePage(UserRelationScenePageReqVO reqVO);

    List<UserRelationSceneRespVO> getSceneSimpleList();

    UserRelationSceneDO getSceneByCode(String code);

    UserRelationSceneDO getEnabledSceneByCode(String code);

}
