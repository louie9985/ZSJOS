package cn.iocoder.yudao.module.zsjos.dal.dataobject.contactnote;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("zsjos_contact_note")
public class ContactNoteDO extends TenantBaseDO {
    @TableId private Long id;
    private String subjectType;
    private Long subjectId;
    private Long authorId;
    private String authorName;
    private String content;
    private String requestKey;
}
