package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonContactClaimDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonContactClaimMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CONTACT_CONFLICT;

@Service
public class PersonIdentityWriteService {
    @Resource private PersonMapper personMapper;
    @Resource private PersonContactClaimMapper claimMapper;
    @Resource private PersonNumberService personNumberService;

    @Transactional(rollbackFor = Exception.class)
    public PersonDO createNew(String name, String mobile, String wechatId, String identityStatus) {
        Reservation reservation = reserve(mobile, wechatId);
        if (!reservation.personIds().isEmpty()) throw exception(LEAD_CONTACT_CONFLICT);
        PersonDO person = newPerson(name, mobile, wechatId, identityStatus);
        personMapper.insert(person);
        claimMapper.bindReservations(tenantId(), reservation.key(), person.getId());
        return person;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonDO resolveOrCreate(String name, String mobile, String wechatId, String identityStatus) {
        Reservation reservation = reserve(mobile, wechatId);
        if (reservation.personIds().size() > 1) throw exception(LEAD_CONTACT_CONFLICT);
        if (reservation.personIds().size() == 1) {
            PersonDO person = personMapper.selectByIdForUpdate(reservation.personIds().iterator().next(), tenantId());
            if (person == null) throw exception(LEAD_CONTACT_CONFLICT);
            claimMapper.bindReservations(tenantId(), reservation.key(), person.getId());
            return person;
        }
        PersonDO person = newPerson(name, mobile, wechatId, identityStatus);
        personMapper.insert(person);
        claimMapper.bindReservations(tenantId(), reservation.key(), person.getId());
        return person;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonDO update(Long personId, String name, String mobile, String wechatId) {
        PersonDO person = personMapper.selectByIdForUpdate(personId, tenantId());
        if (person == null) throw exception(LEAD_CONTACT_CONFLICT);
        Reservation reservation = reserve(mobile, wechatId);
        if (reservation.personIds().stream().anyMatch(id -> !Objects.equals(id, personId))) {
            throw exception(LEAD_CONTACT_CONFLICT);
        }
        person.setName(name); person.setMobile(normalize(mobile)); person.setWechatId(normalize(wechatId));
        person.setLastSeenAt(LocalDateTime.now()); personMapper.updateById(person);
        claimMapper.bindReservations(tenantId(), reservation.key(), personId);
        claimMapper.deleteStale(tenantId(), personId, reservation.values());
        return person;
    }

    private Reservation reserve(String mobile, String wechatId) {
        List<String> values = java.util.stream.Stream.of(normalize(mobile), normalize(wechatId))
                .filter(Objects::nonNull).distinct().sorted().toList();
        String key = UUID.randomUUID().toString();
        Set<Long> personIds = new LinkedHashSet<>();
        for (String value : values) {
            claimMapper.reserve(tenantId(), value, key);
            PersonContactClaimDO claim = claimMapper.selectByValueForUpdate(tenantId(), value);
            if (claim == null) throw exception(LEAD_CONTACT_CONFLICT);
            if (claim.getPersonId() != null) personIds.add(claim.getPersonId());
            else if (!Objects.equals(claim.getReservationKey(), key)) throw exception(LEAD_CONTACT_CONFLICT);
        }
        return new Reservation(key, values, personIds);
    }

    private PersonDO newPerson(String name, String mobile, String wechatId, String identityStatus) {
        LocalDateTime now = LocalDateTime.now();
        PersonDO person = new PersonDO();
        person.setPersonNo(personNumberService.next());
        person.setName(name); person.setMobile(normalize(mobile)); person.setWechatId(normalize(wechatId));
        person.setIdentityStatus(identityStatus); person.setFirstSeenAt(now); person.setLastSeenAt(now); person.setVersion(0);
        return person;
    }

    private Long tenantId() { return TenantContextHolder.getRequiredTenantId(); }
    private String normalize(String value) { return StrUtil.trimToNull(value); }
    private record Reservation(String key, List<String> values, Set<Long> personIds) {}
}
