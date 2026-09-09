package cn.iocoder.yudao.module.zsjos.dal.dataobject.content;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("zsjos_content_version_file")
@KeySequence("zsjos_content_version_file_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentVersionFileDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contentVersionId;
    private String fieldKey;
    private Integer sortNo;
    private Long infraFileId;
    private String fileUrlSnapshot;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long uploadedByUserId;
}
