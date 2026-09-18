package cn.iocoder.yudao.module.eam.framework.approval.content;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.transfer.EamTransferDO;
import cn.iocoder.yudao.module.eam.enums.transfer.EamTransferStatusEnum;
import cn.iocoder.yudao.module.eam.service.transfer.EamTransferService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EAM 资产流转（领用/退还/借用/归还/调拨）审批的业务内容。
 *
 * <p>businessKey 是<b>四段式</b> {@code asset-transfer:{id}:round:{roundNo}}
 * （见 {@code EamTransferServiceImpl} 发起处）。
 *
 * <p>⚠️ 与多数业务域不同，这里的 id 在**第 2 段而不是第 1 段**——第 1 段是字面量
 * {@code asset-transfer}。因此用 {@code idSegment(rest, 0)} 取的是剥离前缀后的首段，
 * 那种"统一取 split(\":\")[1]"的写法在这里会解析失败（取到字面量，非数字）。
 *
 * <p>展示字段大量复用 DO 上的人名/部门名快照（{@code *NameSnapshot}），
 * 审批人看到的应是申请当时的样子，而不是人事变动后的当前值。
 */
@Component
public class EamTransferContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = "asset-transfer:";

    @Resource
    private EamTransferService transferService;

    @Override
    public String bizType() {
        return "eam_transfer";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        // 剥离前缀后形如 "12:round:1"，业务 id 是首段。
        return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, businessKey));
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        EamTransferDO row = load(businessId, viewerId);
        if (row == null) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(row.getNo() + " · " + row.getAssetNameSnapshot());
        brief.setSubtitle(typeText(row) + " · " + statusText(row.getStatus()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("流转类型", typeText(row)),
                BpmApprovalFieldVO.of("资产", row.getAssetNameSnapshot()),
                BpmApprovalFieldVO.of("转出 → 接收", transferPath(row)),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        EamTransferDO row = load(businessId, viewerId);
        if (row == null) {
            return null;
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(row.getNo() + " · " + row.getAssetNameSnapshot());
        card.setStatusText(statusText(row.getStatus()));

        card.getGroups().add(group("流转信息", List.of(
                BpmApprovalFieldVO.of("流转单号", row.getNo()),
                BpmApprovalFieldVO.of("流转类型", typeText(row)),
                BpmApprovalFieldVO.of("状态", statusText(row.getStatus())),
                BpmApprovalFieldVO.of("轮次", row.getRoundNo() == null ? null : "第 " + row.getRoundNo() + " 轮"),
                BpmApprovalFieldVO.of("申请时间", BpmApprovalFormat.dateTime(row.getApplyTime())),
                BpmApprovalFieldVO.of("申请人", row.getApplyUserNameSnapshot()))));

        card.getGroups().add(group("资产", List.of(
                BpmApprovalFieldVO.of("资产编号", row.getAssetCodeSnapshot()),
                BpmApprovalFieldVO.of("资产名称", row.getAssetNameSnapshot()))));

        card.getGroups().add(group("交接", List.of(
                BpmApprovalFieldVO.of("转出员工", row.getFromEmployeeNameSnapshot()),
                BpmApprovalFieldVO.of("转出部门", row.getFromDeptNameSnapshot()),
                BpmApprovalFieldVO.of("接收员工", row.getToEmployeeNameSnapshot()),
                BpmApprovalFieldVO.of("接收部门", row.getToDeptNameSnapshot()))));

        List<BpmApprovalFieldVO> schedule = new ArrayList<>();
        schedule.add(BpmApprovalFieldVO.of("预计归还", BpmApprovalFormat.date(row.getExpectedReturnDate())));
        schedule.add(BpmApprovalFieldVO.of("实际归还", BpmApprovalFormat.date(row.getActualReturnDate())));
        schedule.add(BpmApprovalFieldVO.of("申请部门", row.getApplyDeptNameSnapshot()));
        card.getGroups().add(group("其他", schedule));

        if (row.getReason() != null) {
            card.getGroups().add(wideGroup("申请事由", row.getReason()));
        }
        // 验收信息只在借用类流转走到验收环节后才有值。
        if (row.getInspectionResult() != null || row.getInspectionRemark() != null) {
            card.getGroups().add(group("验收", List.of(
                    BpmApprovalFieldVO.of("验收结果", inspectionText(row.getInspectionResult())),
                    BpmApprovalFieldVO.wide("验收备注", row.getInspectionRemark()))));
        }
        return card;
    }

    /**
     * 复用业务侧带数据范围的查询：无权时返回 null，Provider 自然降级为"不展示业务内容"，
     * 审批中心仍能展示流程本身。这与 Controller 的 /get 走的是同一套口径。
     */
    private EamTransferDO load(String businessId, Long viewerId) {
        try {
            return transferService.getTransfer(Long.valueOf(businessId), viewerId);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String transferPath(EamTransferDO row) {
        String from = row.getFromEmployeeNameSnapshot();
        String to = row.getToEmployeeNameSnapshot();
        if (from == null && to == null) {
            return null;
        }
        return (from == null ? "—" : from) + " → " + (to == null ? "—" : to);
    }

    private static String typeText(EamTransferDO row) {
        // 类型中文名在发起时已快照，优先用它，避免枚举改名后历史单据显示错乱。
        return row.getTypeLabelSnapshot();
    }

    private static String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        for (EamTransferStatusEnum item : EamTransferStatusEnum.values()) {
            if (item.getStatus().equals(status)) {
                return item.getName();
            }
        }
        return String.valueOf(status);
    }

    private static String inspectionText(Integer result) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case 1 -> "验收通过";
            case 2 -> "验收不通过";
            default -> String.valueOf(result);
        };
    }

    private static BpmApprovalDetailVO.Group group(String title, List<BpmApprovalFieldVO> fields) {
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(fields));
        return group;
    }

    private static BpmApprovalDetailVO.Group wideGroup(String title, String value) {
        BpmApprovalDetailVO.Group group = group(title, List.of(BpmApprovalFieldVO.of(title, value)));
        group.setSpan(true);
        return group;
    }
}
