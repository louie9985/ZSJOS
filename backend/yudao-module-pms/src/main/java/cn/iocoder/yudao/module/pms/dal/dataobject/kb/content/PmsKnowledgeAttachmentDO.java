package cn.iocoder.yudao.module.pms.dal.dataobject.kb.content;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_knowledge_attachment")
public class PmsKnowledgeAttachmentDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long documentId;
    private Long fileId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private Integer sort;
    private Boolean shared;
}
