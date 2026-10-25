package cn.iocoder.yudao.module.zsjos.service.studententry;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studententry.vo.StudentEntryVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.collaboration.CollaborationGroupDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.collaboration.CollaborationGroupMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonContactClaimMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentEntryCommandMapper;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactNotifyPublisher;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Service
public class StudentEntryService {
    public static final String PERMISSION = "zsjos:student:create";
    public static final ErrorCode CONTACT_INVALID = new ErrorCode(1_900_010_100, "请填写有效手机号或微信号");
    public static final ErrorCode CONTACT_CONFLICT = new ErrorCode(1_900_010_101, "联系方式存在冲突，请由有权人员核实");
    public static final ErrorCode REUSE_REQUIRED = new ErrorCode(1_900_010_102, "人员档案已存在，请重新核对并确认复用");
    public static final ErrorCode TARGET_INVALID = new ErrorCode(1_900_010_103, "所选编导、班级或课程已失效，请刷新后重选");
    public static final ErrorCode COMMAND_CONFLICT = new ErrorCode(1_900_010_104, "重复提交的内容不一致，请重新打开录入表单");
    public static final ErrorCode FORM_INVALID = new ErrorCode(1_900_010_105, "学员录入参数不完整或不匹配");
    @Resource private PermissionApi permissionApi;
    @Resource private PostApi postApi;
    @Resource private AdminUserApi userApi;
    @Resource private PersonMapper personMapper;
    @Resource private PersonContactClaimMapper claimMapper;
    @Resource private PersonIdentityWriteService identities;
    @Resource private ServiceRelationMapper relations;
    @Resource private DeliveryClassService classes;
    @Resource private DeliveryClassMapper classMapper;
    @Resource private ZsjosProductSkuService products;
    @Resource private CollaborationGroupMapper groups;
    @Resource private StudentEntryCommandMapper commands;
    @Resource private BusinessEventMapper events;
    @Resource private StudentContactNotifyPublisher notifications;
    @Resource private StudentEntryPersonAccess personAccess;

    private void authorize(Long actor) {
        if (!permissionApi.hasAnyPermissions(actor, PERMISSION)) throw exception(STUDENT_PERMISSION_DENIED);
    }

    public Options options(Long actor) {
        authorize(actor);
        var post = postApi.getPostByCode(CONTENT_DIRECTOR_POST_CODE);
        var directors = post == null || !CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus()) ? List.<User>of()
                : userApi.getUserListByPostIds(Set.of(post.getId())).stream()
                    .filter(u -> CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus()))
                    .map(u -> new User(u.getId(), u.getNickname())).sorted(Comparator.comparing(User::id)).toList();
        var catalog = products.getLeadCatalog();
        var courses = catalog.skus().stream().map(sku -> new Course(sku.skuRef(),
                catalog.spus().stream().filter(spu -> Objects.equals(spu.spuRef(), sku.spuRef()))
                    .map(spu -> spu.spuName() + " / " + sku.skuName()).findFirst().orElse(sku.skuName()))).toList();
        var eligibleClasses = classes.options(null, true).stream().filter(c -> {
            if (DeliveryClassKind.PENDING.equals(c.getClassKind()) || Boolean.TRUE.equals(c.getSystemClass()) && c.getHomeroomUserId() == null) return true;
            if (c.getHomeroomUserId() == null) return false;
            try { classes.validateHomeroom(c.getHomeroomUserId()); return true; }
            catch (cn.iocoder.yudao.framework.common.exception.ServiceException invalid) { return false; }
        }).toList();
        return new Options(directors, eligibleClasses, courses);
    }

    @Transactional(readOnly=true)
    public Check check(Long actor, Contact request) {
        authorize(actor); validateContact(request.mobile(), request.wechatId());
        Set<Long> matches = matches(request.mobile(), request.wechatId());
        if (matches.isEmpty()) return new Check("new", null, null, null, List.of());
        if (matches.size() != 1) return new Check("conflict", null, null, null, List.of());
        Long personId = matches.iterator().next();
        if (!personAccess.hasPermission(personId, "reuse", actor)) return new Check("occupied", null, null, null, List.of());
        var person = personMapper.selectById(personId);
        if (person == null) return new Check("conflict", null, null, null, List.of());
        var visible = personRelations(personId).stream().filter(r -> readable(r, actor))
                .map(r -> new ExistingService(r.getId(), r.getStudentNameSnapshot(), r.getStatus(), switch (r.getStatus()) { case "active" -> "服务中"; case "paused" -> "已暂停"; case "completed" -> "已结业"; case "cancelled" -> "已取消"; default -> "状态未记录"; })).toList();
        return new Check("reuse", personId, person.getPersonNo(), person.getName(), visible);
    }

    @Transactional(rollbackFor=Exception.class)
    public Result create(Long actor, Create req) {
        authorize(actor); validateContact(req.mobile(), req.wechatId());
        boolean media = "media".equals(req.entryType());
        if ((!media && !"teaching".equals(req.entryType())) || StrUtil.isBlank(req.name())
                || (media && (req.directorUserId() == null || req.classId() != null))
                || (!media && (req.classId() == null || req.directorUserId() != null))
                || (!req.confirmReuse() && req.reusePersonId() != null)) throw exception(FORM_INVALID);
        Long tenant = TenantContextHolder.getRequiredTenantId();
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(req));
        // A unique, transaction-held command reservation serializes even first-use concurrent retries.
        commands.reserve(tenant, actor, req.idempotencyKey(), hash);
        var command = commands.lock(tenant, actor, req.idempotencyKey());
        if (command == null || !hash.equals(command.getRequestHash())) throw exception(COMMAND_CONFLICT);
        if (command.getServiceRelationId() != null) return result(actor, command.getPersonId(), command.getServiceRelationId());
        var currentOptions = options(actor);
        if (media && currentOptions.directors().stream().noneMatch(d -> Objects.equals(d.id(), req.directorUserId()))) {
            throw exception(TARGET_INVALID);
        }
        boolean teaching = !media || req.teachingDelivery();
        Long classId = req.classId();
        if (media && teaching) {
            var pending = currentOptions.classes().stream().filter(c -> DeliveryClassKind.PENDING.equals(c.getClassKind())
                    || Boolean.TRUE.equals(c.getSystemClass()) && c.getHomeroomUserId() == null).toList();
            if (pending.size() != 1) throw exception(TARGET_INVALID);
            classId = pending.getFirst().getId();
        }
        Long ownerId = null;
        if (teaching) {
            Long selectedId = classId;
            if (currentOptions.classes().stream().noneMatch(c -> Objects.equals(c.getId(), selectedId))) throw exception(TARGET_INVALID);
            var selected = classMapper.selectByIdForUpdate(classId, tenant);
            if (selected == null || !"SERVING".equals(selected.getStatus())
                    || (!DeliveryClassKind.pending(selected) && !DeliveryClassKind.assignable(selected))) throw exception(TARGET_INVALID);
            if (!DeliveryClassKind.pending(selected)) ownerId = classes.validateHomeroom(selected.getHomeroomUserId()).getId();
        }
        String courseSnapshot = null;
        if (StrUtil.isNotBlank(req.skuRef())) {
            var sku = products.getLeadCatalog().skus().stream().filter(s -> Objects.equals(s.skuRef(), req.skuRef())).findFirst()
                    .orElseThrow(() -> exception(TARGET_INVALID));
            courseSnapshot = JsonUtils.toJsonString(products.validateLeadProduct(sku.spuRef(), false, sku.skuRef(), false));
        }
        // Contact claims lock the identity before the final match check; reusing never adds/changes contacts.
        var values = contactValues(req.mobile(), req.wechatId());
        for (String value : values) { claimMapper.reserve(tenant, value, "student-entry:" + command.getId()); claimMapper.selectByValueForUpdate(tenant, value); }
        Set<Long> matched = matches(req.mobile(), req.wechatId());
        if (matched.size() > 1) throw exception(CONTACT_CONFLICT);
        PersonDO person;
        if (!matched.isEmpty()) {
            Long personId = matched.iterator().next();
            personAccess.checkReuse(personId, actor);
            if (!req.confirmReuse() || !Objects.equals(req.reusePersonId(), personId)) throw exception(REUSE_REQUIRED);
            person = personMapper.selectByIdForUpdate(personId, tenant);
            if (person == null) throw exception(CONTACT_CONFLICT);
            // An extra unmatched contact would otherwise imply silently modifying the reused identity.
            if (values.stream().anyMatch(v -> !Objects.equals(v, StrUtil.trimToNull(person.getMobile()))
                    && !Objects.equals(v, StrUtil.trimToNull(person.getWechatId())))) throw exception(CONTACT_CONFLICT);
            // Legacy identities may predate claims; bind only reservations made by this command.
            claimMapper.bindReservations(tenant, "student-entry:" + command.getId(), personId);
            if (!"student".equals(person.getIdentityStatus())) { person.setIdentityStatus("student"); person.setVersion(person.getVersion()+1); personMapper.updateById(person); }
        } else {
            if (req.confirmReuse() || req.reusePersonId() != null) throw exception(REUSE_REQUIRED);
            person = identities.createReserved(req.name().trim(), StrUtil.trimToNull(req.mobile()), StrUtil.trimToNull(req.wechatId()), "student", "student-entry:" + command.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        var relation = new ServiceRelationDO();
        relation.setPersonId(person.getId()); relation.setStudentNameSnapshot(req.name().trim());
        relation.setStudentNameSource("independent_entry"); relation.setTeachingDelivery(teaching);
        relation.setMediaReady(media); relation.setClassId(classId); relation.setOwnerUserId(ownerId);
        relation.setStatus("active"); relation.setAcceptanceStatus("pending"); relation.setTeachingClosed(false);
        relation.setContentDirectorUserId(media ? req.directorUserId() : null);
        relation.setDirectorStage(media ? "precheck" : null); relation.setServiceSnapshot(courseSnapshot);
        relation.setActivatedAt(now); relation.setVersion(0); relations.insert(relation);
        var group = new CollaborationGroupDO(); group.setSourceServiceRelationId(relation.getId());
        group.setStudentPersonId(person.getId()); group.setDirectorUserId(relation.getContentDirectorUserId());
        group.setStatus("active"); group.setVersion(0); groups.insert(group);
        relation.setCollaborationGroupId(group.getId()); relations.updateById(relation);
        command.setPersonId(person.getId()); command.setServiceRelationId(relation.getId()); commands.updateById(command);
        var event = new BusinessEventDO(); event.setEventType("student_independent_entry"); event.setAggregateType("student-service");
        event.setAggregateId(relation.getId()); event.setOperatorUserId(actor); event.setOccurredAt(now);
        event.setIdempotencyKey("student-entry:" + command.getId());
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("personId", person.getId(), "entryType", req.entryType(),
                "teachingDelivery", teaching, "reusedPerson", req.confirmReuse()))); events.insert(event);
        Map<String,Object> context = new LinkedHashMap<>(); context.put("studentNo", person.getPersonNo());
        context.put("studentName", relation.getStudentNameSnapshot()); context.put("orderNo", "");
        if (ownerId != null) { context.put("studyPlannerUserId", ownerId); notifications.publish(NOTIFY_SCENE_PLANNER_ASSIGNED,
                relation.getId(), "student-entry-planner:" + relation.getId(), null, now, context); }
        if (media) { context.put("contentDirectorUserId", req.directorUserId()); notifications.publish(NOTIFY_SCENE_DIRECTOR_ASSIGNED,
                relation.getId(), "student-entry-director:" + relation.getId(), null, now, context); }
        return result(actor, person.getId(), relation.getId());
    }

    private Result result(Long actor, Long personId, Long relationId) {
        var person = personMapper.selectById(personId);
        var relation = relations.selectById(relationId);
        return new Result(personId, person == null ? null : person.getPersonNo(), relationId, relation != null && readable(relation, actor));
    }
    private boolean readable(ServiceRelationDO relation, Long actor) {
        return personAccess.canReadService(relation, actor);
    }
    List<ServiceRelationDO> personRelations(Long id) {
        return relations.selectList(new LambdaQueryWrapperX<ServiceRelationDO>().eq(ServiceRelationDO::getPersonId, id));
    }
    private Set<Long> matches(String mobile, String wechat) {
        var values = contactValues(mobile, wechat); var ids = new LinkedHashSet<Long>();
        claimMapper.selectList(new LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonContactClaimDO>()
                .in(cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonContactClaimDO::getContactValue, values))
                .stream().map(c -> c.getPersonId()).filter(Objects::nonNull).forEach(ids::add);
        // Read historical master contacts as well; missing claims must never cause duplicate people.
        personMapper.selectList(new LambdaQueryWrapperX<PersonDO>().and(q -> q.in(PersonDO::getMobile, values)
                .or().in(PersonDO::getWechatId, values))).stream().map(PersonDO::getId).forEach(ids::add);
        return ids;
    }
    private List<String> contactValues(String mobile, String wechat) {
        return java.util.stream.Stream.of(StrUtil.trimToNull(mobile), StrUtil.trimToNull(wechat))
                .filter(Objects::nonNull).distinct().sorted().toList();
    }
    private void validateContact(String mobile, String wechat) {
        if (wechat != null && wechat.trim().length() > 64 || contactValues(mobile, wechat).isEmpty() || StrUtil.isNotBlank(mobile) && !ValidationUtils.isMobile(mobile.trim())) throw exception(CONTACT_INVALID);
    }
}
