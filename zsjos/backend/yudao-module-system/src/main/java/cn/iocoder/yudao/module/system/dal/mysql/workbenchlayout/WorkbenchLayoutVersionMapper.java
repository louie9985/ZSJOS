package cn.iocoder.yudao.module.system.dal.mysql.workbenchlayout;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.workbenchlayout.WorkbenchLayoutVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkbenchLayoutVersionMapper extends BaseMapperX<WorkbenchLayoutVersionDO> {

    default List<WorkbenchLayoutVersionDO> selectListByLayoutId(Long layoutId) {
        return selectList(new LambdaQueryWrapperX<WorkbenchLayoutVersionDO>()
                .eq(WorkbenchLayoutVersionDO::getLayoutId, layoutId)
                .orderByDesc(WorkbenchLayoutVersionDO::getVersionNo));
    }

}
