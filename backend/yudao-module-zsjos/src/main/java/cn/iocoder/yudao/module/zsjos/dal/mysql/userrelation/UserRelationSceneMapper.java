package cn.iocoder.yudao.module.zsjos.dal.mysql.userrelation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.scene.UserRelationScenePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserRelationSceneMapper extends BaseMapperX<UserRelationSceneDO> {

    default UserRelationSceneDO selectByCode(String code) {
        return selectOne(UserRelationSceneDO::getCode, code);
    }

    default PageResult<UserRelationSceneDO> selectPage(UserRelationScenePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<UserRelationSceneDO>()
                .likeIfPresent(UserRelationSceneDO::getName, reqVO.getName())
                .likeIfPresent(UserRelationSceneDO::getCode, reqVO.getCode())
                .eqIfPresent(UserRelationSceneDO::getStatus, reqVO.getStatus())
                .orderByAsc(UserRelationSceneDO::getId));
    }

    default List<UserRelationSceneDO> selectSimpleList() {
        return selectList(new LambdaQueryWrapperX<UserRelationSceneDO>()
                .orderByAsc(UserRelationSceneDO::getId));
    }

}
