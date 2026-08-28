package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceRelationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BpmProcessInstanceRelationMapper extends BaseMapperX<BpmProcessInstanceRelationDO> {

    default List<BpmProcessInstanceRelationDO> selectListBySource(String sourceProcessInstanceId) {
        return selectList(new LambdaQueryWrapperX<BpmProcessInstanceRelationDO>()
                .eq(BpmProcessInstanceRelationDO::getSourceProcessInstanceId, sourceProcessInstanceId)
                .orderByAsc(BpmProcessInstanceRelationDO::getFormField)
                .orderByAsc(BpmProcessInstanceRelationDO::getSort));
    }

}
