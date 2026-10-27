package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassGroupVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.*;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassGroupErrors.*;

@Service
public class DeliveryClassGroupService {
    @Resource private DeliveryClassGroupMapper groups;
    @Resource private DeliveryClassMapper classes;
    @Resource private DeliveryClassMembershipEventMapper events;
    @Resource private DeliveryClassServiceImpl classService;
    @Resource private DeliveryClassService classCommands;
    @Resource private DeliveryClassGroupPermissionProvider authorization;
    @Resource private DeliveryClassObjectPermissionProvider classAuthorization;
    @Resource private AdminUserApi users;
    @Resource private DeptApi departments;

    static String key(DeliveryClassDO row) {
        if (DeliveryClassKind.UNSCHEDULED.equals(row.getClassKind())) return "system:UNSCHEDULED";
        if (DeliveryClassKind.exception(row)) return "system:EXCEPTION";
        return row.getGroupId() == null ? "class:" + row.getId() : "group:" + row.getGroupId();
    }

    private LambdaQueryWrapperX<DeliveryClassDO> matching(Long userId, DeliveryClassPageReqVO req, boolean managed) {
        var copy = BeanUtils.toBean(req, DeliveryClassPageReqVO.class);
        copy.setKeyword(null);
        var query = classService.scopedQuery(userId, copy, managed);
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            String keyword = req.getKeyword().trim();
            query.and(q -> q.like(DeliveryClassDO::getClassName, keyword).or().like(DeliveryClassDO::getClassNo, keyword)
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_delivery_class_group cg WHERE cg.id=zsjos_delivery_class.group_id "
                            + "AND cg.tenant_id=zsjos_delivery_class.tenant_id AND cg.deleted=0 AND cg.name LIKE {0})", "%"+keyword+"%"));
        }
        return query;
    }

    public Page page(Long userId, DeliveryClassPageReqVO req, boolean managed) {
        // Group the complete authorized match set before top-level pagination, never a page of classes.
        List<DeliveryClassDO> matches = classes.selectList(matching(userId, req, managed));
        Map<String, List<DeliveryClassDO>> buckets = matches.stream().collect(Collectors.groupingBy(
                DeliveryClassGroupService::key, LinkedHashMap::new, Collectors.toList()));
        Map<Long, DeliveryClassGroupDO> groupMap = groups.selectList().stream()
                .collect(Collectors.toMap(DeliveryClassGroupDO::getId, g -> g));
        var maintenance = authorization.maintenanceContext(userId);
        List<Node> nodes = new ArrayList<>();
        for (var entry : buckets.entrySet()) {
            var first = entry.getValue().getFirst();
            var group = first.getGroupId() == null ? null : groupMap.get(first.getGroupId());
            if (entry.getKey().startsWith("group:") && group == null) throw exception(MISSING);
            nodes.add(node(entry.getKey(), entry.getValue(), group, maintenance, req.getKeyword()));
        }
        if (managed && req.getReadScope() == null && req.getTargetUserId() == null && emptyMatches(req)) {
            Set<Long> occupied = classes.selectList(new LambdaQueryWrapperX<DeliveryClassDO>().isNotNull(DeliveryClassDO::getGroupId))
                    .stream().map(DeliveryClassDO::getGroupId).collect(Collectors.toSet());
            groupMap.values().stream().filter(g -> !occupied.contains(g.getId()) && maintenance != null && maintenance.any(g))
                    .filter(g -> req.getKeyword() == null || g.getName().contains(req.getKeyword().trim()))
                    .forEach(g -> nodes.add(node("group:"+g.getId(), List.of(), g, maintenance, req.getKeyword())));
        }
        Comparator<Node> defaultOrder = Comparator.comparingInt(DeliveryClassGroupService::rank)
                .thenComparing(Node::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Node::getSortId, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Node::getKey);
        if (req.getSortField() != null) {
            // Aggregate the authorized match set before sorting, so counts and dates span every page.
            enrichNodes(nodes, matches, userId);
            Comparator<Node> requested = switch (req.getSortField()) {
                case "name" -> Comparator.comparing(Node::getName, valueOrder(req));
                case "count" -> Comparator.comparing(Node::getServiceCount, valueOrder(req));
                case "date" -> Comparator.comparing(n -> buckets.getOrDefault(n.getKey(), List.of()).stream()
                        .map(r -> DeliveryClassDates.fromSnapshot(r.getExamScheduleSnapshot()))
                        .filter(Objects::nonNull).map(DeliveryClassDates::start)
                        .min(Comparator.naturalOrder()).orElse(null), valueOrder(req));
                default -> throw exception(INVALID);
            };
            nodes.sort(Comparator.comparingInt(DeliveryClassGroupService::rank).thenComparing(requested).thenComparing(defaultOrder));
        } else nodes.sort(defaultOrder);
        List<Node> selected = slice(nodes, req.getPageNo(), req.getPageSize());
        if (req.getSortField() == null) enrichNodes(selected, matches, userId);
        return new Page(selected, nodes.size(), matches.size());
    }

    private static <T extends Comparable<? super T>> Comparator<T> valueOrder(DeliveryClassPageReqVO req) {
        return Comparator.nullsLast("desc".equals(req.getSortOrder()) ? Comparator.<T>reverseOrder() : Comparator.<T>naturalOrder());
    }

    private void enrichNodes(List<Node> selected, List<DeliveryClassDO> matches, Long userId) {
        Set<String> selectedKeys = selected.stream().map(Node::getKey).collect(Collectors.toSet());
        List<DeliveryClassDO> selectedClasses = matches.stream().filter(c -> selectedKeys.contains(key(c))).toList();
        List<DeliveryClassRespVO> values = selectedClasses.isEmpty() ? List.of()
                : classService.toPageResult(new PageResult<>(selectedClasses, (long) selectedClasses.size()), userId).getList();
        Map<Long,String> classKeys=selectedClasses.stream().collect(Collectors.toMap(DeliveryClassDO::getId,DeliveryClassGroupService::key));
        Map<String,List<DeliveryClassRespVO>> details = values.stream().collect(Collectors.groupingBy(v -> classKeys.get(v.getId())));
        for (Node n : selected) enrich(n, details.getOrDefault(n.getKey(), List.of()));
    }

    private boolean emptyMatches(DeliveryClassPageReqVO req) {
        return !"COMPLETED".equals(req.getStatus()) && req.getHomeroomUserId()==null && req.getCategoryId()==null
                && req.getExamScheduleId()==null && req.getProjectCategoryId()==null && req.getExamDateStart()==null
                && req.getExamDateEnd()==null && !Boolean.TRUE.equals(req.getAssignableOnly());
    }
    static int rank(Node node) {
        if ("system:UNSCHEDULED".equals(node.getKey())) return 1;
        if ("system:EXCEPTION".equals(node.getKey())) return 3;
        if (node.getSingle()!=null && DeliveryClassKind.PENDING.equals(node.getSingle().getClassKind())) return 0;
        return 2;
    }
    static <T> List<T> slice(List<T> rows, int page, int size) {
        int start = (int)Math.min(rows.size(), Math.max(0L, ((long)page-1)*size));
        return rows.subList(start, Math.min(rows.size(), start+size));
    }
    private Node node(String key, List<DeliveryClassDO> rows, DeliveryClassGroupDO group, DeliveryClassGroupPermissionProvider.MaintainContext maintenance, String keyword) {
        Node node = new Node(); node.setKey(key); node.setClassCount(rows.size());
        node.setType(key.startsWith("system:") ? "SYSTEM" : group != null ? "GROUP" : "CLASS");
        node.setName(group!=null ? group.getName() : key.equals("system:EXCEPTION") ? "异常结业"
                : key.equals("system:UNSCHEDULED") ? "未确定考期" : rows.getFirst().getClassName());
        if (group!=null) node.setGroup(groupVO(group,maintenance));
        var first=rows.isEmpty()?null:rows.getFirst();
        node.setCreateTime(group!=null?group.getCreateTime():first==null?null:first.getCreateTime());
        node.setSortId(group!=null?group.getId():first==null?null:first.getId());
        if ("CLASS".equals(node.getType())) {
            var single = new DeliveryClassRespVO(); single.setId(first.getId()); single.setClassKind(first.getClassKind());
            node.setSingle(single);
        }
        node.setAutoExpand(!"CLASS".equals(node.getType()) && keyword!=null && !keyword.isBlank()
                && !node.getName().contains(keyword.trim()));
        return node;
    }
    private void enrich(Node n, List<DeliveryClassRespVO> rows) {
        if ("CLASS".equals(n.getType()) && !rows.isEmpty()) n.setSingle(rows.getFirst());
        n.setServiceCount(rows.stream().mapToLong(r -> r.getStudentCount()==null?0:r.getStudentCount()).sum());
        n.setHomeroomCount(rows.stream().map(DeliveryClassRespVO::getHomeroomUserId).filter(Objects::nonNull).distinct().count());
        n.setServingCount(rows.stream().filter(r -> "SERVING".equals(r.getStatus())).count());
        n.setCompletedCount(rows.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count());
        Map<Long,DeliveryClassProjectRespVO> projects=new LinkedHashMap<>();
        rows.forEach(r -> r.getProjects().forEach(p -> projects.putIfAbsent(p.id(),p))); n.setProjects(List.copyOf(projects.values()));
        Set<String> dates=rows.stream().map(r -> r.getStartDate()==null ? "未设置" : r.getStartDate()+
                (r.getEndDate()!=null&&!r.getEndDate().equals(r.getStartDate())?" ~ "+r.getEndDate():""))
                .collect(Collectors.toSet());
        n.setExamDate("SYSTEM".equals(n.getType())?"—":dates.isEmpty()?"—":dates.size()==1?dates.iterator().next():"多个考期");
    }

    public PageResult<DeliveryClassRespVO> children(Long userId, String key, DeliveryClassPageReqVO req, boolean managed) {
        var query=matching(userId,req,managed);
        if (key.equals("system:UNSCHEDULED")||key.equals("system:EXCEPTION")) {
            query.eq(DeliveryClassDO::getClassKind,key.substring(7));
        } else if (key.matches("group:[1-9][0-9]*")) {
            long id;
            try { id=Long.parseLong(key.substring(6)); }
            catch (NumberFormatException invalid) { throw exception(INVALID); }
            if (groups.selectById(id)==null) throw exception(MISSING);
            authorization.check(id,"read",userId);
            query.eq(DeliveryClassDO::getGroupId,id);
        } else throw exception(INVALID);
        if (req.getSortField() == null) return classService.toPageResult(classes.selectPage(req,query), userId);
        var matches = classes.selectList(query);
        var rows = new ArrayList<>(classService.toPageResult(new PageResult<>(matches, (long) matches.size()), userId).getList());
        Comparator<DeliveryClassRespVO> requested = switch (req.getSortField()) {
            case "name" -> Comparator.comparing(DeliveryClassRespVO::getClassName, valueOrder(req));
            case "date" -> Comparator.comparing(DeliveryClassRespVO::getStartDate, valueOrder(req));
            case "count" -> Comparator.comparing(DeliveryClassRespVO::getStudentCount, valueOrder(req));
            default -> throw exception(INVALID);
        };
        rows.sort(requested.thenComparing(DeliveryClassRespVO::getId, Comparator.reverseOrder()));
        return new PageResult<>(slice(rows, req.getPageNo(), req.getPageSize()), (long) rows.size());
    }

    @ZsjosPermission(bizType="delivery-class-group",bizId="#id",action="read")
    public PageResult<DeliveryClassRespVO> groupChildren(Long id,Long userId,DeliveryClassPageReqVO req,boolean managed) {
        return children(userId,"group:"+id,req,managed);
    }

    private boolean maintainAny(DeliveryClassGroupDO g,Long user) {
        return authorization.canMaintain(g,user,"create")||authorization.canMaintain(g,user,"update")||authorization.canMaintain(g,user,"delete");
    }
    private Group groupVO(DeliveryClassGroupDO row,DeliveryClassGroupPermissionProvider.MaintainContext context) {
        Group result=BeanUtils.toBean(row,Group.class);
        result.setCanCreate(context != null && context.can(row,"create"));
        result.setCanUpdate(context != null && context.can(row,"update"));
        result.setCanDelete(context != null && context.can(row,"delete")); return result;
    }
    public PageResult<Group> options(Long userId, DeliveryClassPageReqVO req) {
        var current=users.getUser(userId);
        if(current==null||current.getDeptId()==null) throw exception(DENIED);
        var query=new LambdaQueryWrapperX<DeliveryClassGroupDO>().eq(DeliveryClassGroupDO::getDeptId,current.getDeptId())
                .likeIfPresent(DeliveryClassGroupDO::getName,req.getKeyword()).orderByDesc(DeliveryClassGroupDO::getId);
        // Filter authorization before paging; this also permits choosing empty groups without read grants.
        var maintenance = authorization.maintenanceContext(userId);
        var rows=groups.selectList(query).stream().filter(g -> maintenance != null && maintenance.any(g)).map(g -> groupVO(g,maintenance)).toList();
        return new PageResult<>(slice(rows,req.getPageNo(),req.getPageSize()),(long)rows.size());
    }

    private DeliveryClassGroupDO lock(Long id) {
        var row=groups.lock(id,TenantContextHolder.getRequiredTenantId());
        if(row==null) throw exception(MISSING); return row;
    }
    private void requireVersion(Integer actual,Integer expected) {
        if(expected==null||!Objects.equals(actual,expected)) throw exception(CONFLICT);
    }
    private Long insertGroup(Save req,Long userId) {
        var user=users.getUser(userId);
        if(user==null||user.getDeptId()==null) throw exception(DENIED);
        var group=new DeliveryClassGroupDO(); group.setName(req.getName().trim()); group.setDeptId(user.getDeptId());
        if(!authorization.canMaintain(group,userId,"create")) throw exception(DENIED);
        var dept=departments.getDept(user.getDeptId()); group.setDeptNameSnapshot(dept==null?null:dept.getName());
        group.setReferenceDate(req.getReferenceDate()); group.setVersion(0); groups.insert(group); return group.getId();
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosAudit(action="delivery-class-group.create",targetType="delivery-class-group")
    public Long create(Save req,Long userId) { return insertGroup(req,userId); }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="delivery-class-group",bizId="#id",action="update")
    @ZsjosAudit(action="delivery-class-group.update",targetType="delivery-class-group")
    public void update(Long id,Save req,Long userId) {
        var row=lock(id); authorization.check(id,"update",userId); requireVersion(row.getVersion(),req.getVersion());
        groups.update(null,new LambdaUpdateWrapper<DeliveryClassGroupDO>().eq(DeliveryClassGroupDO::getId,id)
                .set(DeliveryClassGroupDO::getName,req.getName().trim()).set(DeliveryClassGroupDO::getReferenceDate,req.getReferenceDate())
                .set(DeliveryClassGroupDO::getVersion,row.getVersion()+1));
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="delivery-class-group",bizId="#id",action="delete")
    @ZsjosAudit(action="delivery-class-group.delete",targetType="delivery-class-group")
    public void delete(Long id,Integer version,Long userId) {
        var row=lock(id); authorization.check(id,"delete",userId); requireVersion(row.getVersion(),version);
        if(classes.selectCount(new LambdaQueryWrapperX<DeliveryClassDO>().eq(DeliveryClassDO::getGroupId,id))>0) throw exception(NOT_EMPTY);
        groups.deleteById(id);
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosAudit(action="delivery-class-group.create-classes",targetType="delivery-class-group")
    public Created createClasses(CreateClasses req,Long userId) {
        Long groupId=null;
        if("NEW".equals(req.getGroupMode())) {
            if(req.getNewGroup()==null) throw exception(INVALID); groupId=insertGroup(req.getNewGroup(),userId);
        } else if("EXISTING".equals(req.getGroupMode())) {
            if(req.getGroupId()==null) throw exception(INVALID);
            var group=lock(req.getGroupId());
            if(!authorization.canMaintain(group,userId,"create")) throw exception(DENIED); groupId=group.getId();
        } else if(!"NONE".equals(req.getGroupMode())) throw exception(INVALID);
        var ids=classCommands.createBatch(req,userId);
        if(groupId!=null) {
            for(Long id:ids) {
                var row=classes.selectById(id); var group=groups.selectById(groupId);
                if(!Objects.equals(row.getDeptId(),group.getDeptId())) throw exception(DEPARTMENT);
                classes.update(null,new LambdaUpdateWrapper<DeliveryClassDO>().eq(DeliveryClassDO::getId,id).set(DeliveryClassDO::getGroupId,groupId));
                membershipEvent(id,null,groupId,userId);
            }
        }
        return new Created(groupId,ids);
    }

    public PageResult<DeliveryClassRespVO> candidates(Long userId,DeliveryClassPageReqVO req) {
        var current=users.getUser(userId); if(current==null||current.getDeptId()==null) throw exception(DENIED);
        var query=classService.scopedQuery(userId,req,true).eq(DeliveryClassDO::getDeptId,current.getDeptId())
                .eq(DeliveryClassDO::getSystemClass,false);
        var page=classService.toPageResult(classes.selectPage(req,query), userId);
        Set<Long> ids=page.getList().stream().map(DeliveryClassRespVO::getGroupId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long,String> names=ids.isEmpty()?Map.of():groups.selectList(new LambdaQueryWrapperX<DeliveryClassGroupDO>().in(DeliveryClassGroupDO::getId,ids))
                .stream().collect(Collectors.toMap(DeliveryClassGroupDO::getId,DeliveryClassGroupDO::getName));
        page.getList().forEach(r -> r.setGroupName(names.get(r.getGroupId()))); return page;
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosAudit(action="delivery-class-group.move",targetType="delivery-class-group")
    public void move(Move req,Long userId) {
        Map<Long,Integer> expected=new TreeMap<>();
        req.getClasses().forEach(c -> { if(expected.put(c.getId(),c.getVersion())!=null) throw exception(INVALID); });
        List<DeliveryClassDO> observed=classes.selectList(new LambdaQueryWrapperX<DeliveryClassDO>().in(DeliveryClassDO::getId,expected.keySet()));
        if(observed.size()!=expected.size()) throw exception(CONFLICT);
        SortedSet<Long> groupIds=observed.stream().map(DeliveryClassDO::getGroupId).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        if(req.getTargetGroupId()!=null) groupIds.add(req.getTargetGroupId());
        Map<Long,DeliveryClassGroupDO> locked=new HashMap<>();
        for(Long id:groupIds) { var group=lock(id); authorization.check(id,"update",userId); locked.put(id,group); }
        var target=req.getTargetGroupId()==null?null:locked.get(req.getTargetGroupId());
        for(var entry:expected.entrySet()) {
            var row=classes.selectByIdForUpdate(entry.getKey(),TenantContextHolder.getRequiredTenantId());
            if(row==null) throw exception(CONFLICT);
            requireVersion(row.getVersion(),entry.getValue());
            if(row.getGroupId()!=null&&!locked.containsKey(row.getGroupId())) throw exception(CONFLICT);
            classAuthorization.check(row.getId(),"update",userId);
            var user=users.getUser(userId);
            if(user==null||!Objects.equals(user.getDeptId(),row.getDeptId())) throw exception(DENIED);
            if(Boolean.TRUE.equals(row.getSystemClass())||!(row.getClassKind()==null||DeliveryClassKind.NORMAL.equals(row.getClassKind()))) throw exception(INVALID);
            if(target!=null&&!Objects.equals(row.getDeptId(),target.getDeptId())) throw exception(DEPARTMENT);
            classes.update(null,new LambdaUpdateWrapper<DeliveryClassDO>().eq(DeliveryClassDO::getId,row.getId())
                    .set(DeliveryClassDO::getGroupId,req.getTargetGroupId()).set(DeliveryClassDO::getVersion,row.getVersion()+1));
            membershipEvent(row.getId(),row.getGroupId(),req.getTargetGroupId(),userId);
        }
    }
    private void membershipEvent(Long classId,Long source,Long target,Long userId) {
        if(Objects.equals(source,target)) return;
        var event=new DeliveryClassMembershipEventDO(); event.setClassId(classId); event.setSourceGroupId(source);
        event.setTargetGroupId(target); event.setOperatorUserId(userId); events.insert(event);
    }
}
