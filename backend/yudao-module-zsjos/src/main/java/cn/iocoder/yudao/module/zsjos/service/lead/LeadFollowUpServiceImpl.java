package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpImageDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadFollowUpServiceImpl implements LeadFollowUpService {
    @Resource private LeadMapper leadMapper;
    @Resource private LeadFollowUpRecordMapper recordMapper;
    @Resource private LeadFollowUpImageMapper imageMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadLifecycleTaskService lifecycleTaskService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadFollowUpRespVO create(Long leadId, Long operatorUserId, LeadFollowUpCreateReqVO reqVO) {
        LocalDateTime occurredAt = LocalDateTime.now();
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                || !STATUS_SUBMITTED.equals(lead.getStatus())
                || !Objects.equals(operatorUserId, lead.getOwnerUserId())
                || lead.getCurrentAssignmentHistoryId() == null) {
            throw exception(LEAD_FOLLOW_UP_STATE_INVALID);
        }
        LeadFollowUpRecordDO existing = recordMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (existing != null) {
            if (!Objects.equals(existing.getLeadId(), leadId)) {
                throw exception(LEAD_FOLLOW_UP_IDEMPOTENCY_CONFLICT);
            }
            return convert(existing, imageMapper.selectListByRecordIds(List.of(existing.getId())),
                    adminUserApi.getUserMap(List.of(existing.getOperatorUserId())), null);
        }
        if (reqVO.getNextFollowUpAt() != null && !reqVO.getNextFollowUpAt().isAfter(occurredAt)) {
            throw exception(LEAD_FOLLOW_UP_TIME_INVALID);
        }

        DictDataRespDTO method = requireEnabledDict(DICT_FOLLOW_UP_METHOD, reqVO.getMethod());
        DictDataRespDTO result = requireEnabledDict(DICT_FOLLOW_UP_RESULT, reqVO.getResult());
        DictDataRespDTO beforeCategory = findDict(DICT_CATEGORY, lead.getLeadCategory());
        DictDataRespDTO afterCategory = Objects.equals(lead.getLeadCategory(), reqVO.getLeadCategory())
                ? beforeCategory : requireEnabledDict(DICT_CATEGORY, reqVO.getLeadCategory());
        AdminUserRespDTO operator = adminUserApi.getUser(operatorUserId);
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(reqVO.getImages(), operatorUserId);

        LeadFollowUpRecordDO record = new LeadFollowUpRecordDO();
        record.setLeadId(leadId);
        record.setAssignmentHistoryId(lead.getCurrentAssignmentHistoryId());
        record.setOperatorUserId(operatorUserId);
        record.setOwnerUserIdSnapshot(lead.getOwnerUserId());
        record.setOwnerDeptIdSnapshot(operator == null ? null : operator.getDeptId());
        record.setMethodValue(method.getValue());
        record.setMethodLabelSnapshot(method.getLabel());
        record.setResultValue(result.getValue());
        record.setResultLabelSnapshot(result.getLabel());
        record.setCategoryBefore(lead.getLeadCategory());
        record.setCategoryBeforeLabelSnapshot(labelOf(beforeCategory, lead.getLeadCategory()));
        record.setCategoryAfter(reqVO.getLeadCategory());
        record.setCategoryAfterLabelSnapshot(labelOf(afterCategory, reqVO.getLeadCategory()));
        record.setRemark(reqVO.getRemark());
        record.setNextFollowUpAt(reqVO.getNextFollowUpAt());
        record.setOccurredAt(occurredAt);
        record.setFirstInAssignment(false);
        record.setIdempotencyKey(reqVO.getIdempotencyKey());
        recordMapper.insert(record);

        for (int index = 0; index < reqVO.getImages().size(); index++) {
            FileInfoRespDTO file = files.get(reqVO.getImages().get(index).getInfraFileId());
            LeadFollowUpImageDO image = new LeadFollowUpImageDO();
            image.setFollowUpRecordId(record.getId());
            image.setInfraFileId(file.getId());
            image.setOriginalName(file.getName());
            image.setContentType(file.getType());
            image.setFileSize(file.getSize());
            image.setSort(index);
            imageMapper.insert(image);
        }

        if (!Objects.equals(lead.getLeadCategory(), reqVO.getLeadCategory())) {
            BusinessEventDO categoryEvent = addEvent(EVENT_LEAD_CATEGORY_CHANGED, lead, operatorUserId, record.getId(), occurredAt,
                    lead.getLeadCategory(), reqVO.getLeadCategory());
            Map<String, Object> categoryContext = eventContext(lead, operatorUserId);
            categoryContext.put("category.before", record.getCategoryBeforeLabelSnapshot());
            categoryContext.put("category.after", record.getCategoryAfterLabelSnapshot());
            notifyEventPublisher.publish(CATEGORY_CHANGED, leadId, categoryEvent.getIdempotencyKey(), operatorUserId,
                    occurredAt, categoryContext);
            lead.setLeadCategory(reqVO.getLeadCategory());
        }
        boolean first = lifecycleTaskService.completeFirstFollowUpTask(
                lead.getCurrentAssignmentHistoryId(), occurredAt);
        if (first) {
            record.setFirstInAssignment(true);
            recordMapper.updateById(record);
            lead.setCurrentAssignmentFirstFollowUpAt(occurredAt);
            lifecycleTaskService.createQualificationTask(lead, operatorUserId, occurredAt);
        }
        lifecycleTaskService.replaceFollowUpReminder(leadId, operatorUserId, record.getId(),
                reqVO.getNextFollowUpAt(), occurredAt);
        lead.setLastFollowUpAt(occurredAt);
        lead.setLastFollowUpRecordId(record.getId());
        lead.setNextFollowUpAt(reqVO.getNextFollowUpAt());
        lead.setFollowUpCount((lead.getFollowUpCount() == null ? 0 : lead.getFollowUpCount()) + 1);
        leadMapper.updateById(lead);
        BusinessEventDO followUpEvent = addEvent(EVENT_LEAD_FOLLOW_UP_RECORDED, lead, operatorUserId,
                record.getId(), occurredAt, null, null);
        Map<String, Object> followUpContext = eventContext(lead, operatorUserId);
        followUpContext.put("followUp.method", record.getMethodLabelSnapshot());
        followUpContext.put("followUp.result", record.getResultLabelSnapshot());
        followUpContext.put("followUp.remark", record.getRemark());
        followUpContext.put("followUp.nextAt", record.getNextFollowUpAt());
        notifyEventPublisher.publish(FOLLOW_UP_RECORDED, leadId, followUpEvent.getIdempotencyKey(), operatorUserId,
                occurredAt, followUpContext);
        return convert(record, imageMapper.selectListByRecordIds(List.of(record.getId())),
                Map.of(operatorUserId, operator), null);
    }

    @Override
    public PageResult<LeadFollowUpRespVO> getPage(Long leadId, int pageNo, int pageSize) {
        PageResult<LeadFollowUpRecordDO> page = recordMapper.selectPageByLeadId(leadId, pageNo, pageSize);
        List<Long> recordIds = page.getList().stream().map(LeadFollowUpRecordDO::getId).toList();
        List<LeadFollowUpImageDO> images = imageMapper.selectListByRecordIds(recordIds);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(page.getList().stream()
                .map(LeadFollowUpRecordDO::getOperatorUserId).collect(Collectors.toSet()));
        List<Long> fileIds = images.stream().map(LeadFollowUpImageDO::getInfraFileId).distinct().toList();
        Map<Long, String> urls = fileIds.isEmpty() ? Map.of()
                : fileApi.presignGetUrls(fileIds, ATTACHMENT_URL_EXPIRATION_SECONDS);
        Map<Long, List<LeadFollowUpImageDO>> imagesByRecord = images.stream()
                .collect(Collectors.groupingBy(LeadFollowUpImageDO::getFollowUpRecordId));
        return new PageResult<>(page.getList().stream().map(record -> convert(record,
                imagesByRecord.getOrDefault(record.getId(), List.of()), users, urls)).toList(), page.getTotal());
    }

    private DictDataRespDTO requireEnabledDict(String type, String value) {
        DictDataRespDTO data = findDict(type, value);
        if (data == null || !CommonStatusEnum.ENABLE.getStatus().equals(data.getStatus())) {
            throw exception(LEAD_FOLLOW_UP_DICT_INVALID);
        }
        return data;
    }

    private DictDataRespDTO findDict(String type, String value) {
        try {
            return dictDataApi.getDictDataList(type).stream()
                    .filter(item -> Objects.equals(item.getValue(), value)).findFirst().orElse(null);
        } catch (ServiceException ex) {
            throw exception(LEAD_FOLLOW_UP_DICT_INVALID);
        }
    }

    private String labelOf(DictDataRespDTO data, String fallback) {
        return data == null ? fallback : data.getLabel();
    }

    private BusinessEventDO addEvent(String type, LeadDO lead, Long operatorUserId, Long recordId,
                          LocalDateTime occurredAt, String from, String to) {
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(type);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(lead.getId());
        event.setOperatorUserId(operatorUserId);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("followUpRecordId", recordId)));
        event.setOccurredAt(occurredAt);
        event.setIdempotencyKey(type + ":" + recordId);
        eventMapper.insert(event);
        return event;
    }

    private Map<String, Object> eventContext(LeadDO lead, Long operatorUserId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", lead.getSourceUserId());
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("operatorUserId", operatorUserId);
        return context;
    }

    private LeadFollowUpRespVO convert(LeadFollowUpRecordDO record, List<LeadFollowUpImageDO> images,
                                       Map<Long, AdminUserRespDTO> users, Map<Long, String> urls) {
        LeadFollowUpRespVO result = new LeadFollowUpRespVO();
        result.setId(record.getId()); result.setLeadId(record.getLeadId());
        result.setAssignmentHistoryId(record.getAssignmentHistoryId());
        result.setOperatorUserId(record.getOperatorUserId());
        AdminUserRespDTO user = users.get(record.getOperatorUserId());
        result.setOperatorName(user == null ? null : user.getNickname());
        result.setOccurredAt(record.getOccurredAt()); result.setFirstInAssignment(record.getFirstInAssignment());
        result.setMethod(record.getMethodValue()); result.setMethodLabel(record.getMethodLabelSnapshot());
        result.setResult(record.getResultValue()); result.setResultLabel(record.getResultLabelSnapshot());
        result.setCategoryBefore(record.getCategoryBefore());
        result.setCategoryBeforeLabel(record.getCategoryBeforeLabelSnapshot());
        result.setCategoryAfter(record.getCategoryAfter());
        result.setCategoryAfterLabel(record.getCategoryAfterLabelSnapshot());
        result.setRemark(record.getRemark()); result.setNextFollowUpAt(record.getNextFollowUpAt());
        result.setImages(images.stream().map(image -> {
            LeadFollowUpRespVO.ImageVO item = new LeadFollowUpRespVO.ImageVO();
            item.setInfraFileId(image.getInfraFileId()); item.setOriginalName(image.getOriginalName());
            item.setContentType(image.getContentType()); item.setFileSize(image.getFileSize());
            item.setSort(image.getSort()); item.setUrl(urls == null ? null : urls.get(image.getInfraFileId()));
            return item;
        }).toList());
        return result;
    }
}
