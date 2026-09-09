package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionFileDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ContentVersionFileMapper extends BaseMapperX<ContentVersionFileDO> {

    default List<ContentVersionFileDO> selectByVersionId(Long contentVersionId) {
        return selectList(new LambdaQueryWrapperX<ContentVersionFileDO>()
                .eq(ContentVersionFileDO::getContentVersionId, contentVersionId)
                .orderByAsc(ContentVersionFileDO::getFieldKey)
                .orderByAsc(ContentVersionFileDO::getSortNo));
    }
}
