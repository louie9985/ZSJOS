package cn.iocoder.yudao.module.pms.dal.dataobject.kb.importing;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_knowledge_import_task")
public class PmsKnowledgeImportTaskDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long ownerId;
    private Long libraryId;
    private Long folderId;
    private String rootName;
    private String requestKey;
    private String manifestHash;
    private String mode;
    private String status;
}
