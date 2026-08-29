package cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO; import com.baomidou.mybatisplus.annotation.TableName; import lombok.Data; import lombok.EqualsAndHashCode; import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_forced_form_version") public class ForcedFormVersionDO extends TenantBaseDO { private Long id; private Long formId; private Integer versionNo; private String fieldsJson; private String schemaHash; private String status; private LocalDateTime publishedAt; }
