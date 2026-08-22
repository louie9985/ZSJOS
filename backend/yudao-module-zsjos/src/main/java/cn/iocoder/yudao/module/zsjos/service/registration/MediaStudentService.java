package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTargetRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentTalkSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentops.MediaStudentTalkRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentops.MediaStudentTalkRecordMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountService;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_STUDENT_INVALID;

@Service
public class MediaStudentService {
    @Resource private MyStudentService myStudentService;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private PositioningCardMapper positioningMapper;
    @Resource private ContentMapper contentMapper;
    @Resource private ProductionTicketMapper ticketMapper;
    @Resource private MediaAccountService accountService;
    @Resource private ContentService contentService;
    @Resource private PositioningCardService positioningService;
    @Resource private MediaStudentTalkRecordMapper talkRecordMapper;
    @Resource private AdminUserApi adminUserApi;

    public MediaStudentDetailRespVO getDetail(Long userId, Long personId) {
        MediaStudentDetailRespVO result = new MediaStudentDetailRespVO();
        result.setStudent(myStudentService.getMediaStudent(userId, personId));
        List<MediaAccountDO> accounts = accountMapper.selectByParticipantAndStudent(userId, personId);
        List<Long> accountIds = accounts.stream().map(MediaAccountDO::getId).toList();
        result.setAccounts(accounts.stream().map(account -> {
            MediaStudentDetailRespVO.AccountVO row = BeanUtils.toBean(account, MediaStudentDetailRespVO.AccountVO.class);
            row.setPlatformLabel(account.getPlatformLabelSnapshot());
            row.setStage(account.getSStage());
            var accountDetail = accountService.get(account.getId(), userId);
            row.setAvailableActions(accountDetail.getAvailableActions());
            row.setDetailSnapshots(accountDetail.getDetailSnapshots());
            return row;
        }).toList());
        result.setPositioningCards(positioningMapper.selectByStudentAndAccountIds(personId, accountIds).stream()
                .map(row -> {
                    MediaStudentDetailRespVO.PositioningVO value = BeanUtils.toBean(row, MediaStudentDetailRespVO.PositioningVO.class);
                    value.setAvailableActions(positioningService.get(row.getId(), userId).getAvailableActions());
                    return value;
                }).toList());
        result.setContents(contentMapper.selectByAccountIds(accountIds).stream()
                .map(row -> {
                    MediaStudentDetailRespVO.ContentVO value = BeanUtils.toBean(row, MediaStudentDetailRespVO.ContentVO.class);
                    value.setAvailableActions(contentService.get(row.getId(), userId).getAvailableActions());
                    return value;
                }).toList());
        result.setProductionTickets(ticketMapper.selectByAccountIds(accountIds).stream()
                .map(row -> BeanUtils.toBean(row, MediaStudentDetailRespVO.TicketVO.class)).toList());
        return result;
    }

    public MediaStudentTargetRespVO resolveTarget(Long userId, String bizType, Long bizId) {
        MediaAccountDO account;
        String tab;
        if ("media-account".equals(bizType)) {
            accountService.get(bizId, userId);
            account = accountMapper.selectById(bizId);
            tab = "accounts";
        } else if ("content".equals(bizType)) {
            var content = contentService.get(bizId, userId);
            account = accountMapper.selectById(content.getAccountId());
            tab = "content";
        } else if ("positioning-card".equals(bizType)) {
            var card = positioningService.get(bizId, userId);
            account = accountMapper.selectById(card.getAccountId());
            tab = "positioning";
        } else {
            throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        }
        if (account == null || account.getStudentPersonId() == null) throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        getDetail(userId, account.getStudentPersonId());
        return new MediaStudentTargetRespVO(account.getStudentPersonId(), tab, bizId);
    }

    public List<MediaStudentTalkRespVO> getTalkRecords(Long userId, Long personId) {
        myStudentService.getMediaStudent(userId, personId);
        List<MediaStudentTalkRecordDO> records = talkRecordMapper.selectByStudent(personId);
        var users = adminUserApi.getUserMap(records.stream().map(MediaStudentTalkRecordDO::getOperatorUserId).collect(java.util.stream.Collectors.toSet()));
        return records.stream().map(record -> {
            MediaStudentTalkRespVO response = new MediaStudentTalkRespVO();
            response.setId(record.getId()); response.setAccountId(record.getAccountId());
            response.setOperatorUserId(record.getOperatorUserId()); response.setContent(record.getContent());
            response.setOccurredAt(record.getOccurredAt());
            response.setAttachmentFileIds(record.getAttachmentFileIdsJson() == null ? List.of()
                    : JsonUtils.parseArray(record.getAttachmentFileIdsJson(), Long.class));
            var operator = users.get(record.getOperatorUserId());
            response.setOperatorUserName(operator == null ? null : operator.getNickname());
            return response;
        }).toList();
    }

    public Long createTalkRecord(Long userId, Long personId, MediaStudentTalkSaveReqVO request) {
        myStudentService.getMediaStudent(userId, personId);
        if (request.getAccountId() != null && accountMapper.selectByParticipantAndStudent(userId, personId).stream()
                .noneMatch(account -> account.getId().equals(request.getAccountId()))) {
            throw exception(MEDIA_ACCOUNT_STUDENT_INVALID);
        }
        MediaStudentTalkRecordDO record = new MediaStudentTalkRecordDO();
        record.setStudentPersonId(personId); record.setAccountId(request.getAccountId());
        record.setOperatorUserId(userId); record.setContent(request.getContent().trim());
        record.setAttachmentFileIdsJson(JsonUtils.toJsonString(request.getAttachmentFileIds() == null
                ? List.of() : request.getAttachmentFileIds()));
        record.setOccurredAt(java.time.LocalDateTime.now()); talkRecordMapper.insert(record);
        return record.getId();
    }
}
