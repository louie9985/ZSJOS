package cn.iocoder.yudao.module.system.dal.mysql.ip;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.controller.admin.ip.vo.AreaListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.ip.AreaDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AreaMapper extends BaseMapperX<AreaDO> {

    default List<AreaDO> selectList(AreaListReqVO reqVO) {
        return selectList(new LambdaQueryWrapperX<AreaDO>()
                .likeIfPresent(AreaDO::getName, reqVO.getName())
                .eqIfPresent(AreaDO::getStatus, reqVO.getStatus())
                .orderByAsc(AreaDO::getType, AreaDO::getSort, AreaDO::getId));
    }

    default List<AreaDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<AreaDO>()
                .orderByAsc(AreaDO::getType, AreaDO::getSort, AreaDO::getId));
    }

    default AreaDO selectByParentIdAndName(Integer parentId, String name) {
        return selectOne(AreaDO::getParentId, parentId, AreaDO::getName, name);
    }

    default AreaDO selectByParentIdAndSelectionCode(Integer parentId, String selectionCode) {
        return selectOne(AreaDO::getParentId, parentId, AreaDO::getSelectionCode, selectionCode);
    }

}
