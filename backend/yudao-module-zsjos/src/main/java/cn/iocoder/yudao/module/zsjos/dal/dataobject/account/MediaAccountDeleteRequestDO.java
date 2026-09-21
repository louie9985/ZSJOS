package cn.iocoder.yudao.module.zsjos.dal.dataobject.account;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO; import com.baomidou.mybatisplus.annotation.*; import lombok.*;
@Data @EqualsAndHashCode(callSuper=true) @TableName("zsjos_media_account_delete_request") @KeySequence("zsjos_media_account_delete_request_seq")
public class MediaAccountDeleteRequestDO extends TenantBaseDO { @TableId private Long id; private Long accountId; private String processInstanceId; private String accountSnapshotJson; private String reason; private Long requestedByUserId; private Long reviewerUserId; private String status; private String resultReason; private Integer attemptCount; private String lastError; private Integer version; }
