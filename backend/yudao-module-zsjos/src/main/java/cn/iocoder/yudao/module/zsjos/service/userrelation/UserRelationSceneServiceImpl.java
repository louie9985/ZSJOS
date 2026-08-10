package cn.iocoder.yudao.module.zsjos.service.userrelation;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationScenePageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationSceneSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.userrelation.UserRelationSceneMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class UserRelationSceneServiceImpl implements UserRelationSceneService {

    @Resource
    private UserRelationSceneMapper sceneMapper;
    @Resource
    private LeadAssignmentRelationMapper relationMapper;
    @Resource
    private LeadAssignmentRelationLogMapper relationLogMapper;
    @Resource
    private PostApi postApi;

    @Override
    public Long createScene(UserRelationSceneSaveReqVO reqVO) {
        validateCodeUnique(reqVO.getCode(), null);
        validatePosts(reqVO);
        UserRelationSceneDO scene = BeanUtils.toBean(reqVO, UserRelationSceneDO.class);
        sceneMapper.insert(scene);
        return scene.getId();
    }

    @Override
    public void updateScene(UserRelationSceneSaveReqVO reqVO) {
        UserRelationSceneDO existing = validateSceneExists(reqVO.getId());
        if (!Objects.equals(existing.getCode(), reqVO.getCode())) {
            throw exception(USER_RELATION_SCENE_CODE_IMMUTABLE);
        }
        validatePosts(reqVO);
        sceneMapper.updateById(BeanUtils.toBean(reqVO, UserRelationSceneDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long id) {
        UserRelationSceneDO scene = validateSceneExists(id);
        if (relationMapper.selectCountByScene(scene.getCode()) > 0
                || relationLogMapper.selectCountByScene(scene.getCode()) > 0) {
            throw exception(USER_RELATION_SCENE_IN_USE);
        }
        sceneMapper.deleteById(id);
    }

    @Override
    public UserRelationSceneRespVO getScene(Long id) {
        return BeanUtils.toBean(validateSceneExists(id), UserRelationSceneRespVO.class);
    }

    @Override
    public PageResult<UserRelationSceneRespVO> getScenePage(UserRelationScenePageReqVO reqVO) {
        return BeanUtils.toBean(sceneMapper.selectPage(reqVO), UserRelationSceneRespVO.class);
    }

    @Override
    public List<UserRelationSceneRespVO> getSceneSimpleList() {
        return BeanUtils.toBean(sceneMapper.selectSimpleList(), UserRelationSceneRespVO.class);
    }

    @Override
    public UserRelationSceneDO getSceneByCode(String code) {
        UserRelationSceneDO scene = sceneMapper.selectByCode(code);
        if (scene == null) {
            throw exception(USER_RELATION_SCENE_NOT_EXISTS);
        }
        return scene;
    }

    @Override
    public UserRelationSceneDO getEnabledSceneByCode(String code) {
        UserRelationSceneDO scene = getSceneByCode(code);
        if (!CommonStatusEnum.ENABLE.getStatus().equals(scene.getStatus())) {
            throw exception(USER_RELATION_SCENE_DISABLED);
        }
        return scene;
    }

    private UserRelationSceneDO validateSceneExists(Long id) {
        UserRelationSceneDO scene = sceneMapper.selectById(id);
        if (scene == null) {
            throw exception(USER_RELATION_SCENE_NOT_EXISTS);
        }
        return scene;
    }

    private void validateCodeUnique(String code, Long id) {
        UserRelationSceneDO scene = sceneMapper.selectByCode(code);
        if (scene != null && !Objects.equals(scene.getId(), id)) {
            throw exception(USER_RELATION_SCENE_CODE_DUPLICATE);
        }
    }

    private void validatePosts(UserRelationSceneSaveReqVO reqVO) {
        if (postApi.getPostByCode(reqVO.getSourcePostCode()) == null
                || postApi.getPostByCode(reqVO.getTargetPostCode()) == null) {
            throw exception(USER_RELATION_SCENE_POST_INVALID);
        }
    }

}
