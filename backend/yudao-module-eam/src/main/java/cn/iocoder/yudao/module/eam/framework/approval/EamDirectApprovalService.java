package cn.iocoder.yudao.module.eam.framework.approval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 直通审批实现：不产生审批环节，单据创建后立即生效。
 *
 * 作为兜底实现存在，保证在未接入 BPM 流程定义时资产流转与报废依然可用。
 * 接入 BPM 后由 {@code EamBpmApprovalService} 覆盖本实现。
 */
@Component
@ConditionalOnMissingBean(name = "eamBpmApprovalService")
public class EamDirectApprovalService implements EamApprovalService {

    @Override
    public String start(String definitionKey, String businessKey, String summary) {
        return null;
    }

    @Override
    public boolean approvalRequired() {
        return false;
    }

}
