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
    public PersonDO resolveForLead(String name, String mobile, String wechatId) {
        PersonDO person = resolveOrCreate(name, mobile, wechatId, "lead");
        boolean knownMobile = knownContact(person, mobile, person.getMobile(), "mobile");
        boolean knownWechat = knownContact(person, wechatId, person.getWechatId(), "wechat");
        if (!Objects.equals(normalize(name), normalize(person.getName()))
                || conflictingContact(mobile, person.getMobile()) && !knownMobile
                || conflictingContact(wechatId, person.getWechatId()) && !knownWechat
                || !(knownMobile || knownWechat)) {
            throw exception(LEAD_CONTACT_CONFLICT);
        }
        // Reusing an identity must not overwrite established contacts or its student status.
        if (normalize(person.getMobile()) == null && normalize(mobile) != null
                || normalize(person.getWechatId()) == null && normalize(wechatId) != null) {
            return update(person.getId(), person.getName(),
                    normalize(person.getMobile()) == null ? mobile : person.getMobile(),
                    normalize(person.getWechatId()) == null ? wechatId : person.getWechatId());
        }
        return person;
    }

    private boolean conflictingContact(String incoming, String stored) {
        return normalize(incoming) != null && normalize(stored) != null
                && !normalize(incoming).equals(normalize(stored));
    }

    private boolean knownContact(PersonDO person, String incoming, String stored, String type) {
        return normalize(incoming) != null && (Objects.equals(normalize(incoming), normalize(stored))
                || personMapper.countVerifiedContactAlias(tenantId(),person.getId(),type,normalize(incoming))>0);
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
        Set<String> retained = new LinkedHashSet<>(reservation.values());
        retained.addAll(personMapper.selectVerifiedContactValues(tenantId(),personId));
        claimMapper.deleteStale(tenantId(), personId, new ArrayList<>(retained));
        return person;
    }

    @Transactional(rollbackFor = Exception.class)
    public PersonDO createReserved(String name, String mobile, String wechatId, String identityStatus, String key) {
        Reservation reservation = reserve(mobile, wechatId, key);
        if (!reservation.personIds().isEmpty()) throw exception(LEAD_CONTACT_CONFLICT);
        PersonDO person = newPerson(name, mobile, wechatId, identityStatus);
        personMapper.insert(person);
        claimMapper.bindReservations(tenantId(), key, person.getId());
        return person;
    }

    private Reservation reserve(String mobile, String wechatId) {
        return reserve(mobile, wechatId, UUID.randomUUID().toString());
    }

    private Reservation reserve(String mobile, String wechatId, String key) {
        List<String> values = java.util.stream.Stream.of(normalize(mobile), normalize(wechatId))
                .filter(Objects::nonNull).distinct().sorted().toList();
        Set<Long> personIds = new LinkedHashSet<>();
        for (String value : values) {
            claimMapper.reserve(tenantId(), value, key);
            PersonContactClaimDO claim = claimMapper.selectByValueForUpdate(tenantId(), value);
            if (claim == null) throw exception(LEAD_CONTACT_CONFLICT);
            if (claim.getPersonId() != null) personIds.add(claim.getPersonId());
            else if (!Objects.equals(claim.getReservationKey(), key)) throw exception(LEAD_CONTACT_CONFLICT);
        }
        // Legacy imports can contain real identities without claims. Missing claims must never
        // authorize a second Person, including callers that bypass the submission precheck.
        if (!values.isEmpty()) {
            personMapper.selectDuplicateCandidatesForUpdate(normalize(mobile), normalize(wechatId), tenantId()).stream()
                    .filter(person -> Objects.equals(person.getTenantId(), tenantId()))
                    .map(PersonDO::getId).forEach(personIds::add);
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
