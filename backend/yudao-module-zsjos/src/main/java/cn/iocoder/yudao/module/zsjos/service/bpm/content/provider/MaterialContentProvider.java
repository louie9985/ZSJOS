package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialVersionMapper;
import cn.iocoder.yudao.module.zsjos.enums.MaterialConstants;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalBusinessKey;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalFormat;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFileDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFileMapper;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.ZsjosApprovalAttachmentSupport;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialApprovalObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 素材评审审批的业务内容。
 *
 * <p>businessKey 形如 {@code material-version:12}（{@code MaterialConstants.BUSINESS_KEY_PREFIX}）。
 *
 * <p>这一域的流程定义 key 是**动态的**——不同素材类型可绑定不同流程，
 * 因此绝不能按流程 key 分发；本 Provider 只认 businessKey 前缀，
 * 与 {@code MaterialApprovalService.versionId(task)} 的解析口径保持一致。
 *
 * <p>刻意不复用 {@code MaterialApprovalService.get(versionId, taskId, done, userId)}：
 * 它的签名要求 taskId 与 done，而 Provider 只有 businessId + viewerId。
 * 这里直接读版本 DO，并保留该服务对"快照不可用"的降级语义。
 *
 * <p><b>附件是这一域的审批对象本身</b>（素材正文、封面），所以详情卡必须带附件——
 * 只看标题和摘要，审批人没有判断依据。
 */
@Component
public class MaterialContentProvider implements BpmApprovalContentProvider {

    private static final String PREFIX = MaterialConstants.BUSINESS_KEY_PREFIX;
    private static final String ACTION_READ = "read";
    private static final String COVER_FIELD_KEY = "__cover__";

    @Resource
    private MaterialVersionMapper versionMapper;
    @Resource
    private MaterialFileMapper materialFileMapper;
    @Resource
    private MaterialApprovalObjectPermissionProvider permissionProvider;
    @Resource
    private ZsjosApprovalAttachmentSupport attachmentSupport;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public String bizType() {
        return "material";
    }

    @Override
    public String businessKeyPrefix() {
        return PREFIX;
    }

    @Override
    public String parseBusinessId(String businessKey) {
        return BpmApprovalBusinessKey.idSegment(BpmApprovalBusinessKey.strip(PREFIX, businessKey));
    }

    @Override
    public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
        MaterialVersionDO version = load(businessId);
        if (version == null || !canView(version, viewerId)) {
            return null;
        }
        BpmApprovalBriefVO brief = new BpmApprovalBriefVO();
        brief.setBizType(bizType());
        brief.setTitle(version.getTitle());
        brief.setSubtitle("V" + version.getVersionNo() + " · " + statusText(version.getStatus()));
        brief.setFields(List.of(
                BpmApprovalFieldVO.of("版本", "V" + version.getVersionNo()),
                BpmApprovalFieldVO.of("状态", statusText(version.getStatus())),
                BpmApprovalFieldVO.of("提交人", userName(version.getSubmittedByUserId())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(version.getSubmittedAt()))));
        return brief;
    }

    @Override
    public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
        MaterialVersionDO version = load(businessId);
        if (version == null) {
            return null;
        }
        if (!canView(version, viewerId)) {
            return BpmApprovalDetailVO.noAccess(bizType(), "素材评审");
        }
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();
        card.setBizType(bizType());
        card.setTitle(version.getTitle());
        card.setStatusText(statusText(version.getStatus()));

        card.getGroups().add(group("素材信息", List.of(
                BpmApprovalFieldVO.of("标题", version.getTitle()),
                BpmApprovalFieldVO.of("版本", "V" + version.getVersionNo()),
                BpmApprovalFieldVO.of("状态", statusText(version.getStatus())),
                BpmApprovalFieldVO.of("提交人", userName(version.getSubmittedByUserId())),
                BpmApprovalFieldVO.of("提交时间", BpmApprovalFormat.dateTime(version.getSubmittedAt())),
                BpmApprovalFieldVO.of("生效时间", BpmApprovalFormat.dateTime(version.getEffectiveAt())))));

        if (version.getSummary() != null) {
            card.getGroups().add(wideGroup("摘要", version.getSummary()));
        }
        // 素材正文与封面——这一域的审批对象，缺了它们审批人无从判断。
        addAttachments(card, version.getId());
        if (version.getRejectionReason() != null) {
            card.getGroups().add(wideGroup("驳回原因", version.getRejectionReason()));
        }
        return card;
    }

    /**
     * 把版本下的文件挂成附件字段。封面单独成组，其余按上传顺序。
     *
     * <p>这一域的审批对象就是文件本身，所以附件不是装饰——没有它，审批人只能看标题做判断。
     */
    private void addAttachments(BpmApprovalDetailVO card, Long versionId) {
        List<MaterialFileDO> files;
        try {
            files = materialFileMapper.selectByVersionId(versionId);
        } catch (Exception ex) {
            return;
        }
        if (files == null || files.isEmpty()) {
            return;
        }
        List<BpmApprovalFieldVO.Attachment> covers = new ArrayList<>();
        List<BpmApprovalFieldVO.Attachment> others = new ArrayList<>();
        for (MaterialFileDO file : files) {
            String url = attachmentSupport.resolveUrl(file.getInfraFileId(), file.getFileUrlSnapshot());
            if (url == null) {
                continue;
            }
            BpmApprovalFieldVO.Attachment attachment = BpmApprovalFieldVO.attachment(
                    file.getOriginalName(), url, file.getContentType(), file.getFileSize());
            if (COVER_FIELD_KEY.equals(file.getFieldKey())) {
                covers.add(attachment);
            } else {
                others.add(attachment);
            }
        }
        attachmentSupport.addGroup(card, "封面", covers, true);
        attachmentSupport.addGroup(card, "素材文件", others, true);
    }

    private MaterialVersionDO load(String businessId) {
        try {
            return versionMapper.selectById(Long.valueOf(businessId));
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean canView(MaterialVersionDO version, Long viewerId) {
        try {
            return permissionProvider.hasPermission(version.getId(), ACTION_READ, viewerId);
        } catch (Exception ex) {
            return false;
        }
    }

    private String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        return user == null ? null : user.getNickname();
    }

    private static String statusText(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case MaterialConstants.VERSION_DRAFT -> "草稿";
            case MaterialConstants.VERSION_IN_APPROVAL -> "审批中";
            case MaterialConstants.VERSION_EFFECTIVE -> "已生效";
            case MaterialConstants.VERSION_REJECTED -> "已驳回";
            default -> status;
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
