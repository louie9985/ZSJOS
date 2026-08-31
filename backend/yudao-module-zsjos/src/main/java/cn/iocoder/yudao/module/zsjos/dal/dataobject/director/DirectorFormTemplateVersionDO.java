package cn.iocoder.yudao.module.zsjos.dal.dataobject.director;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("zsjos_director_form_template_version")
@KeySequence("zsjos_director_form_template_version_seq")
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DirectorFormTemplateVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String fieldsJson;
    private Long publishedByUserId;
    private LocalDateTime publishedAt;
    private Integer version;
}
