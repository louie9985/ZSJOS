package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.*;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.*;
import cn.iocoder.yudao.module.zsjos.service.pointsredemption.PointsRequestService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
@Component public class PointsRedemptionContentProvider implements BpmApprovalContentProvider {
    @Resource private PointsRequestService service;
    public String bizType() { return "points_redemption"; }
    public String businessKeyPrefix() { return "points-redemption:"; }
    public String parseBusinessId(String key) { return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(businessKeyPrefix(),key)); }
    public BpmApprovalBriefVO brief(String id, Long viewer) {
        var row = service.get(Long.valueOf(id), viewer);
        var brief = new BpmApprovalBriefVO(); brief.setBizType(bizType()); brief.setTitle(row.getPackageName());
        brief.setSubtitle(row.getRequestNo()); brief.setFields(List.of(BpmApprovalFieldVO.of("积分",row.getCreditsAmount()),
                BpmApprovalFieldVO.of("申请人",row.getApplicantName()))); return brief;
    }
    public BpmApprovalDetailVO detail(String id, Long viewer) {
        var row = service.get(Long.valueOf(id), viewer);
        var result = new BpmApprovalDetailVO(); result.setBizType(bizType()); result.setTitle(row.getRequestNo());
        var group = new BpmApprovalDetailVO.Group(); group.setTitle("积分兑换码申请");
        group.setFields(new ArrayList<>(List.of(BpmApprovalFieldVO.of("申请人",row.getApplicantName()),
                BpmApprovalFieldVO.of("套餐",row.getPackageName()), BpmApprovalFieldVO.of("积分",row.getCreditsAmount()),
                BpmApprovalFieldVO.of("数量",1), BpmApprovalFieldVO.of("有效期",row.getValidDays()==null?"永久有效":row.getValidDays()+"天"),
                BpmApprovalFieldVO.wide("申请理由",row.getReason()))));
        group.getFields().add(BpmApprovalFieldVO.attachments("附件", row.getAttachments().stream()
                .map(a -> BpmApprovalFieldVO.attachment(a.getName(),a.getUrl(),a.getType(),a.getSize())).toList()));
        result.getGroups().add(group); return result;
    }
}
