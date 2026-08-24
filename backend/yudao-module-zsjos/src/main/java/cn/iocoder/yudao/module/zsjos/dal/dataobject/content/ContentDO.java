package cn.iocoder.yudao.module.zsjos.dal.dataobject.content;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("zsjos_content")
@KeySequence("zsjos_content_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ContentDO extends TenantBaseDO {
    @TableId private Long id;
    private String contentNo;
    private Long accountId;
    private Long productionTicketId;
    private String title;
    private String topic;
    private String moduleValue;
    private String moduleLabelSnapshot;
    private String contentClassValue;
    private String contentClassLabelSnapshot;
    private String status;
    private Integer currentVersionNo;
    private String scriptText;
    private String scriptUrl;
    private Long ownerOperatorUserId;
    private Long filmingEditorUserId;
    private String problemCodesJson;
    private String publishedUrl;
    private LocalDateTime publishedAt;
    private Integer rejectCount;
    private Integer version;
}
