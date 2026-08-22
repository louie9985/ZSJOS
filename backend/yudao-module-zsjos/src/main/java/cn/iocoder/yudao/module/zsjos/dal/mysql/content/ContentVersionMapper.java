package cn.iocoder.yudao.module.zsjos.dal.mysql.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ContentVersionMapper extends BaseMapperX<ContentVersionDO> {
    default List<ContentVersionDO> selectByContentId(Long contentId) {
        return selectList(new LambdaQueryWrapper<ContentVersionDO>()
                .eq(ContentVersionDO::getContentId, contentId)
                .orderByDesc(ContentVersionDO::getVersionNo));
    }
}
