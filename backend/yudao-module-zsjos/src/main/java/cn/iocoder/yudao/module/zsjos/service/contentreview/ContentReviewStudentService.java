package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewStudentRespVO.Account;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountProfileService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningSnapshotResourceService;
import cn.iocoder.yudao.module.zsjos.service.positioninginterview.PositioningInterviewService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewSelectionErrors.REVIEW_READ_EXPIRED;

@Service
public class ContentReviewStudentService {
    @Resource private ContentReviewStudentAccess access;
    @Resource private PersonMapper people;
    @Resource private MediaAccountProfileService profiles;
    @Resource private PositioningCardService positioning;
    @Resource private PositioningSnapshotResourceService resources;
    @Resource private PositioningInterviewService interviews;
    @Resource private FileApi files;

    @ZsjosPermission(bizType="content-review-student",bizId="#batchId",action="read")
    public ContentReviewStudentRespVO student(Long batchId, Long personId, Long userId) {
        var scope = access.require(batchId,userId);
        if (!Objects.equals(scope.personId(), personId)) throw exception(REVIEW_READ_EXPIRED);
        var person = people.selectById(personId);
        if (person == null) throw exception(REVIEW_READ_EXPIRED);
        return new ContentReviewStudentRespVO(personId, person.getPersonNo(), person.getName(), person.getMobile(), person.getWechatId(),
                scope.accounts().stream().map(a -> new Account(a.getId(),a.getAccountNo(),a.getNickname(),a.getPlatformLabelSnapshot(),a.getCreateServiceRelationId())).toList());
    }

    @ZsjosPermission(bizType="content-review-student",bizId="#batchId",action="read")
    public PositioningCardService.CardFile file(Long batchId, Long accountId, String kind, Long submissionId, Long fileId, Long userId) {
        var account = access.requireAccount(batchId,accountId,userId);
        String name = null;
        if ("profile".equals(kind)) {
            var profile = profiles.getForReview(batchId,accountId,userId);
            name = profile.getFiles().values().stream().filter(f -> Objects.equals(f.getId(), fileId)).map(f -> f.getName()).findFirst().orElse(null);
        } else if ("interview".equals(kind) && account.getCreateServiceRelationId() != null) {
            var interview = interviews.contextForReview(batchId,account.getCreateServiceRelationId(),userId);
            name = interview.getAttachments().stream().filter(f -> Objects.equals(f.getFileId(), fileId)).map(f -> f.getFileName()).findFirst().orElse(null);
        } else if ("positioning".equals(kind) || "evidence".equals(kind)) {
            var card = positioning.accountForReview(batchId,accountId,userId).stream()
                    .filter(c -> Objects.equals(c.getSubmissionId(),submissionId)).findFirst().orElseThrow(() -> exception(REVIEW_READ_EXPIRED));
            if ("evidence".equals(kind)) {
                name = card.getEvidence() == null ? null : card.getEvidence().stream()
                        .filter(f -> Objects.equals(f.id(),fileId)).map(f -> f.name()).findFirst().orElse(null);
            } else {
                var value = resources.attachmentFromSnapshot(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(card.getDictSnapshot()),fileId);
                name = value.name();
            }
        }
        if (name == null) throw exception(REVIEW_READ_EXPIRED);
        var info = files.getFileInfo(fileId);
        if (info == null) throw exception(REVIEW_READ_EXPIRED);
        return new PositioningCardService.CardFile(fileId,name,info.getType(),info.getSize(),null);
    }

    @ZsjosPermission(bizType="content-review-student",bizId="#batchId",action="read")
    public byte[] fileContent(Long batchId, Long accountId, String kind, Long submissionId, Long fileId, Long userId) throws Exception {
        file(batchId,accountId,kind,submissionId,fileId,userId);
        return files.getFileContent(fileId);
    }

}
