package cn.iocoder.yudao.module.pms.dal.mysql.kb.content;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.PmsKnowledgeAttachmentDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PmsKnowledgeAttachmentMapper extends BaseMapperX<PmsKnowledgeAttachmentDO> {
    default List<PmsKnowledgeAttachmentDO> list(Long documentId) {
        return selectList(new LambdaQueryWrapperX<PmsKnowledgeAttachmentDO>()
                .eq(PmsKnowledgeAttachmentDO::getDocumentId, documentId)
                .orderByAsc(PmsKnowledgeAttachmentDO::getSort, PmsKnowledgeAttachmentDO::getId));
    }
}
