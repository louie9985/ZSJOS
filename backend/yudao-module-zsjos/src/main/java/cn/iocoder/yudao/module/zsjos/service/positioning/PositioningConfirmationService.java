package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.SecureUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningLinkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo.PublicPositioningConfirmationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo.PublicPositioningDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningConfirmationLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningConfirmationLinkMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PositioningConfirmationService {
    @Resource private PositioningCardService cardService;
    @Resource private PositioningCardMapper cardMapper;
    @Resource private PositioningCardSubmissionMapper submissionMapper;
    @Resource private PositioningConfirmationLinkMapper linkMapper;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private MediaWorkflowEventService workflowEventService;
    @Value("${zsjos.positioning.public-base-url:}") private String publicBaseUrl;

    @ZsjosPermission(bizType = BIZ_TYPE_POSITIONING_CARD, bizId = "#cardId", action = "student-link-generate")
    @Transactional(rollbackFor = Exception.class)
    public PositioningLinkRespVO generateLink(Long cardId, Integer cardVersion, Long operatorUserId) {
        String baseUrl = requirePublicBaseUrl();
        PositioningCardDO card = cardService.require(cardId);
        if (!Objects.equals(card.getVersion(), cardVersion)
                || (!POSITIONING_STUDENT_LINK_PENDING.equals(card.getStatus())
                && !POSITIONING_STUDENT_CONFIRM.equals(card.getStatus()))) {
            throw exception(POSITIONING_CARD_STATE_INVALID);
        }
        PositioningCardSubmissionDO submission = cardService.requireLatestSubmission(card, card.getStatus());
        if (!Objects.equals(operatorUserId, card.getOperatorUserId())
                || !Objects.equals(operatorUserId, submission.getOperatorUserId())) {
            throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        }
        LocalDateTime now = LocalDateTime.now();
        linkMapper.revokeActiveBySubmission(submission.getId(), now);
        String rawToken = Base64.encodeUrlSafe(RandomUtil.randomBytes(32));
        PositioningConfirmationLinkDO link = new PositioningConfirmationLinkDO();
        link.setCardId(cardId).setSubmissionId(submission.getId()).setTokenHash(hash(rawToken))
                .setStatus("active").setCreatedByUserId(operatorUserId).setVersion(0);
        linkMapper.insert(link);
        if (POSITIONING_STUDENT_LINK_PENDING.equals(card.getStatus())) {
            if (submissionMapper.markStatus(submission.getId(), submission.getVersion(),
                    POSITIONING_STUDENT_LINK_PENDING, POSITIONING_STUDENT_CONFIRM) == 0
                    || cardMapper.transition(cardId, cardVersion, POSITIONING_STUDENT_LINK_PENDING,
                    POSITIONING_STUDENT_CONFIRM) == 0) {
                throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            }
            workflowEventService.transition(BIZ_TYPE_POSITIONING_CARD, cardId, operatorUserId,
                    POSITIONING_STUDENT_LINK_PENDING, POSITIONING_STUDENT_CONFIRM, null,
                    "positioning-link:" + cardId + ":" + cardVersion);
        }
        return new PositioningLinkRespVO(baseUrl + "/positioning/share#token=" + rawToken);
    }

    private String requirePublicBaseUrl() {
        String baseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        if (baseUrl.isEmpty()) {
            throw exception(POSITIONING_PUBLIC_H5_URL_INVALID);
        }
        try {
            URI uri = URI.create(baseUrl);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!http || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw exception(POSITIONING_PUBLIC_H5_URL_INVALID);
            }
        } catch (IllegalArgumentException ex) {
            throw exception(POSITIONING_PUBLIC_H5_URL_INVALID);
        }
        return baseUrl;
    }

    public PublicPositioningConfirmationRespVO publicDetail(String rawToken) {
        PositioningConfirmationLinkDO link = linkMapper.selectByTokenHash(hash(rawToken));
        if (link == null || "revoked".equals(link.getStatus())) throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        if ("used".equals(link.getStatus())) {
            PublicPositioningConfirmationRespVO response = new PublicPositioningConfirmationRespVO();
            response.setState("processed");
            return response;
        }
        return inTenant(link.getTenantId(), () -> readyDetail(link));
    }

    @Transactional(rollbackFor = Exception.class)
    public void decide(String rawToken, PublicPositioningDecisionReqVO request) {
        if ("request_changes".equals(request.getDecision())
                && (request.getComment() == null || request.getComment().isBlank())) {
            throw exception(POSITIONING_STUDENT_COMMENT_REQUIRED);
        }
        PositioningConfirmationLinkDO located = linkMapper.selectByTokenHash(hash(rawToken));
        if (located == null) throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        inTenant(located.getTenantId(), () -> {
            decideInTenant(rawToken, request);
            return null;
        });
    }

    private PublicPositioningConfirmationRespVO readyDetail(PositioningConfirmationLinkDO link) {
        PositioningCardDO card = cardMapper.selectById(link.getCardId());
        PositioningCardSubmissionDO submission = submissionMapper.selectById(link.getSubmissionId());
        PositioningCardSubmissionDO latest = card == null ? null : submissionMapper.selectLatestByCard(card.getId());
        if (card == null || submission == null || latest == null || !Objects.equals(latest.getId(), submission.getId())
                || !"active".equals(link.getStatus()) || !POSITIONING_STUDENT_CONFIRM.equals(card.getStatus())
                || !POSITIONING_STUDENT_CONFIRM.equals(submission.getStatus())) {
            throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        }
        var account = accountMapper.selectById(submission.getAccountId());
        PublicPositioningConfirmationRespVO response = new PublicPositioningConfirmationRespVO();
        response.setState("ready");
        response.setAccountName(account == null ? null
                : account.getNickname() == null ? account.getAccountNo() : account.getNickname());
        response.setPlatformLabel(account == null ? null : account.getPlatformLabelSnapshot());
        response.setSubmittedAt(submission.getSubmittedAt());
        response.setTrialEndDate(submission.getTrialEndDate());
        response.setFields(submission.getFieldsSnapshotJson() == null ? List.of()
                : JsonUtils.parseArray(submission.getFieldsSnapshotJson(), Object.class));
        response.setValues(submission.getValuesSnapshotJson() == null ? Map.of()
                : JsonUtils.parseObject(submission.getValuesSnapshotJson(), Map.class));
        response.setDictSnapshots(submission.getDictSnapshotJson() == null ? Map.of()
                : JsonUtils.parseObject(submission.getDictSnapshotJson(), Map.class));
        response.setLegacySections(Map.of(
                "定位基础内容", parseJsonObject(submission.getLayer1Json()),
                "定位策略", parseJsonObject(submission.getLayer2Json()),
                "推荐公式", parseJsonObject(submission.getFormulaJson()),
                "可行性评估", parseJsonObject(submission.getFeasibilityJson()),
                "内容形式", parseJsonObject(submission.getContentFormJson()),
                "合规说明", parseJsonObject(submission.getComplianceJson())));
        return response;
    }

    private static Map<String, Object> parseJsonObject(String value) {
        return value == null || value.isBlank() ? Map.of() : JsonUtils.parseObject(value, Map.class);
    }

    private void decideInTenant(String rawToken, PublicPositioningDecisionReqVO request) {
        PositioningConfirmationLinkDO link = linkMapper.selectByTokenHashForUpdate(hash(rawToken));
        if (link == null || !"active".equals(link.getStatus())) throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        PositioningCardDO card = cardMapper.selectByIdForUpdate(link.getCardId(), link.getTenantId());
        PositioningCardSubmissionDO submission = submissionMapper.selectByIdForUpdate(link.getSubmissionId(),
                link.getTenantId());
        PositioningCardSubmissionDO latest = card == null ? null : submissionMapper.selectLatestByCard(card.getId());
        if (card == null || submission == null || latest == null || !Objects.equals(latest.getId(), submission.getId())
                || !POSITIONING_STUDENT_CONFIRM.equals(card.getStatus())
                || !POSITIONING_STUDENT_CONFIRM.equals(submission.getStatus())) {
            throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean agreed = "agree".equals(request.getDecision());
        String submissionStatus = agreed ? "student_agreed" : "change_requested";
        if (submissionMapper.markStudentDecision(submission.getId(), submission.getVersion(),
                POSITIONING_STUDENT_CONFIRM, submissionStatus, request.getDecision(),
                agreed ? null : request.getComment().trim(), now) == 0
                || linkMapper.consume(link.getId(), link.getVersion(), now) == 0) {
            throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        }
        if (agreed) cardService.studentConfirmFromLink(card.getId(), card.getVersion());
        else cardService.studentRejectFromLink(card.getId(), card.getVersion(), request.getComment().trim());
    }

    private static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw exception(POSITIONING_CONFIRMATION_LINK_INVALID);
        return SecureUtil.sha256(rawToken);
    }

    private static <T> T inTenant(Long tenantId, Callable<T> action) {
        Long oldTenantId = TenantContextHolder.getTenantId();
        Boolean oldIgnore = TenantContextHolder.isIgnore();
        try {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(false);
            return action.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } finally {
            TenantContextHolder.setTenantId(oldTenantId);
            TenantContextHolder.setIgnore(oldIgnore);
        }
    }
}
