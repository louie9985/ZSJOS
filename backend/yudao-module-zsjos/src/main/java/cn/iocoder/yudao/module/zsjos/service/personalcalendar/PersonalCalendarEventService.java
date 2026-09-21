package cn.iocoder.yudao.module.zsjos.service.personalcalendar;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventListReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personalcalendar.vo.PersonalCalendarEventSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.PersonalCalendarEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.PersonalCalendarEventMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PERSONAL_CALENDAR_EVENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PERSONAL_CALENDAR_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PERSONAL_CALENDAR_TIME_INVALID;

@Service
public class PersonalCalendarEventService {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Resource private PersonalCalendarEventMapper mapper;
    @Resource private cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService readScopeService;
    @Resource private cn.iocoder.yudao.module.system.api.user.AdminUserApi userApi;

    public List<PersonalCalendarEventRespVO> list(PersonalCalendarEventListReqVO req, Long userId) {
        validateRange(req.getRangeStart(), req.getRangeEnd());
        Long ownerId = req.getReadScope() == null && req.getTargetUserId() == null ? userId
                : readScopeService.resolve(req.getReadScope(), req.getTargetUserId(), userId);
        var rows = ownerId == null ? mapper.selectReadRange(null, req.getRangeStart(), req.getRangeEnd())
                : mapper.selectMyRange(ownerId, req.getRangeStart(), req.getRangeEnd());
        var result = BeanUtils.toBean(rows, PersonalCalendarEventRespVO.class);
        if (!rows.isEmpty()) {
            var users = userApi.getUserMap(rows.stream().map(PersonalCalendarEventDO::getOwnerUserId).distinct().toList());
            result.forEach(row -> {
                var owner = users.get(row.getOwnerUserId());
                row.setOwnerName(owner == null ? "未知账号" : owner.getNickname());
            });
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PersonalCalendarEventSaveReqVO req, Long userId) {
        validateRange(req.getStartTime(), req.getEndTime());
        PersonalCalendarEventDO event = BeanUtils.toBean(req, PersonalCalendarEventDO.class)
                .setOwnerUserId(userId).setTitle(req.getTitle().trim())
                .setStatus(STATUS_ACTIVE).setSourceType(SOURCE_MANUAL);
        mapper.insert(event);
        return event.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PersonalCalendarEventSaveReqVO req, Long userId) {
        validateRange(req.getStartTime(), req.getEndTime());
        requireOwned(id, userId);
        PersonalCalendarEventDO update = BeanUtils.toBean(req, PersonalCalendarEventDO.class)
                .setId(id).setTitle(req.getTitle().trim());
        mapper.updateOwned(update, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        requireOwned(id, userId);
        mapper.deleteOwned(id, userId, java.time.LocalDateTime.now());
    }

    private PersonalCalendarEventDO requireOwned(Long id, Long userId) {
        PersonalCalendarEventDO event = mapper.selectById(id);
        if (event == null) throw exception(PERSONAL_CALENDAR_EVENT_NOT_EXISTS);
        if (!Objects.equals(event.getOwnerUserId(), userId)) throw exception(PERSONAL_CALENDAR_PERMISSION_DENIED);
        return event;
    }

    private static void validateRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) throw exception(PERSONAL_CALENDAR_TIME_INVALID);
    }
}
