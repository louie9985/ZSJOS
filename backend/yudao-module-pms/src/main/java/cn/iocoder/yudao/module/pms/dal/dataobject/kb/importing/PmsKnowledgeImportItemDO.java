package cn.iocoder.yudao.module.pms.dal.dataobject.kb.importing;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pms_knowledge_import_item")
public class PmsKnowledgeImportItemDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long taskId;
    private String relativePath;
    private String pathHash;
    private String title;
    private String fileType;
    private Long fileSize;
    private Long lastModified;
    private String digest;
    private String status;
    private String reason;
    private Long folderId;
    private Long documentId;
    private Long fileId;
    private String fileUrl;
    private String leaseToken;
    private java.time.LocalDateTime leaseUntil;
    private Integer version;
}
