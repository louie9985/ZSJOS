package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.userrelation.vo.relation.UserRelationSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationLogDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.userrelation.UserRelationSceneDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationLogMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.userrelation.UserRelationSceneService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadAssignmentConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadAssignmentServiceImpl implements LeadAssignmentService {

    @Resource
    private LeadAssignmentRelationMapper relationMapper;
    @Resource
    private LeadAssignmentRelationLogMapper relationLogMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private PostApi postApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private UserRelationSceneService sceneService;

    @Override
    public PageResult<LeadAssignmentRelationRespVO> getRelationPage(
            LeadAssignmentRelationPageReqVO reqVO, Long operatorUserId) {
        UserRelationSceneDO scene = sceneService.getSceneByCode(SCENE);
        return getRelationPageInternal(reqVO, operatorUserId, scene, true);
    }

    private PageResult<LeadAssignmentRelationRespVO> getRelationPageInternal(
            LeadAssignmentRelationPageReqVO reqVO, Long operatorUserId,
            UserRelationSceneDO scene, boolean enforceScope) {
        List<AdminUserRespDTO> sourceUsers = getUsersByPostCode(scene.getSourcePostCode(), false).stream()
                .filter(user -> !enforceScope || canManageSourceUser(operatorUserId, user))
                .filter(user -> reqVO.getDeptId() == null || Objects.equals(reqVO.getDeptId(), user.getDeptId()))
                .filter(user -> matchesKeyword(user, reqVO.getKeyword()))
                .sorted(userComparator())
                .toList();
        if (sourceUsers.isEmpty()) {
            return PageResult.empty();
        }
        Map<Long, List<LeadAssignmentRelationDO>> relationMap = getActiveRelationMap(scene.getCode(),
                sourceUsers.stream().map(AdminUserRespDTO::getId).toList());
        if (reqVO.getConfigured() != null) {
            sourceUsers = sourceUsers.stream()
                    .filter(user -> reqVO.getConfigured() == CollUtil.isNotEmpty(relationMap.get(user.getId())))
                    .toList();
        }
        long total = sourceUsers.size();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), sourceUsers.size());
        int to = Math.min(from + reqVO.getPageSize(), sourceUsers.size());
        List<AdminUserRespDTO> pageUsers = sourceUsers.subList(from, to);

        Set<Long> targetUserIds = pageUsers.stream()
                .flatMap(user -> relationMap.getOrDefault(user.getId(), List.of()).stream())
                .map(LeadAssignmentRelationDO::getTargetUserId)
                .collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> targetUserMap = adminUserApi.getUserMap(targetUserIds);
        Set<Long> validSalesIds = getEligibleTargetUsersInternal(scene).stream()
                .map(AdminUserRespDTO::getId).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> deptMap = getDeptMap(pageUsers, targetUserMap.values());
        List<LeadAssignmentRelationRespVO> list = pageUsers.stream()
                .map(user -> buildRelationResp(user, relationMap.getOrDefault(user.getId(), List.of()),
                        targetUserMap, validSalesIds, deptMap))
                .toList();
        return new PageResult<>(list, total);
    }

    @Override
    public List<LeadAssignmentUserRespVO> getEligibleSalesUsers() {
        UserRelationSceneDO scene = sceneService.getEnabledSceneByCode(SCENE);
        return getEligibleTargetUsers(scene);
    }

    private List<LeadAssignmentUserRespVO> getEligibleTargetUsers(UserRelationSceneDO scene) {
        List<AdminUserRespDTO> users = getEligibleTargetUsersInternal(scene).stream()
                .sorted(userComparator()).toList();
        Map<Long, DeptRespDTO> deptMap = getDeptMap(users, List.of());
        return users.stream().map(user -> convertUser(user, deptMap)).toList();
    }

    @Override
    public List<LeadAssignmentUserRespVO> getAssignableSalesUsers(Long sourceUserId) {
        UserRelationSceneDO scene = sceneService.getEnabledSceneByCode(SCENE);
        List<LeadAssignmentRelationDO> relations = relationMapper
                .selectListBySourceUserIds(scene.getCode(), Collections.singleton(sourceUserId)).stream()
                .filter(this::isEnabledRelation)
                .toList();
        if (relations.isEmpty()) {
            return List.of();
        }
        Set<Long> configuredIds = relations.stream().map(LeadAssignmentRelationDO::getTargetUserId)
                .collect(Collectors.toSet());
        List<AdminUserRespDTO> users = getEligibleTargetUsersInternal(scene).stream()
                .filter(user -> configuredIds.contains(user.getId()))
                .sorted(userComparator())
                .toList();
        Map<Long, DeptRespDTO> deptMap = getDeptMap(users, List.of());
        return users.stream().map(user -> convertUser(user, deptMap)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRelations(LeadAssignmentSaveReqVO reqVO, Long operatorUserId) {
        UserRelationSceneDO scene = sceneService.getEnabledSceneByCode(SCENE);
        saveRelationsInternal(reqVO.getSourceUserIds(), reqVO.getTargetUserIds(), reqVO.getMode(),
                operatorUserId, scene, true, true);
    }

    private void saveRelationsInternal(Set<Long> requestedSourceUserIds,
                                       Set<Long> requestedTargetUserIds,
                                       String mode, Long operatorUserId,
                                       UserRelationSceneDO scene,
                                       boolean enforceScope, boolean leadErrors) {
        Set<Long> sourceUserIds = new LinkedHashSet<>(requestedSourceUserIds);
        Set<Long> targetUserIds = requestedTargetUserIds == null
                ? Collections.emptySet() : new LinkedHashSet<>(requestedTargetUserIds);
        if (!Set.of(MODE_APPEND, MODE_REPLACE, MODE_REMOVE).contains(mode)) {
            throw exception(leadErrors ? LEAD_ASSIGNMENT_MODE_INVALID : USER_RELATION_MODE_INVALID);
        }
        Map<Long, AdminUserRespDTO> validSourceMap = getUsersByPostCode(scene.getSourcePostCode(), true).stream()
                .collect(Collectors.toMap(AdminUserRespDTO::getId, Function.identity()));
        for (Long sourceUserId : sourceUserIds) {
            AdminUserRespDTO sourceUser = validSourceMap.get(sourceUserId);
            if (sourceUser == null) {
                throw exception(leadErrors ? LEAD_ASSIGNMENT_SOURCE_INVALID : USER_RELATION_SOURCE_INVALID);
            }
            if (enforceScope && !canManageSourceUser(operatorUserId, sourceUser)) {
                throw exception(LEAD_ASSIGNMENT_SCOPE_DENIED);
            }
        }
        Set<Long> validSalesIds = getEligibleTargetUsersInternal(scene).stream()
                .map(AdminUserRespDTO::getId).collect(Collectors.toSet());
        if (!validSalesIds.containsAll(targetUserIds)) {
            throw exception(leadErrors ? LEAD_ASSIGNMENT_TARGET_INVALID : USER_RELATION_TARGET_INVALID);
        }

        Map<Long, List<LeadAssignmentRelationDO>> existingMap = relationMapper
                .selectListBySourceUserIds(scene.getCode(), sourceUserIds).stream()
                .collect(Collectors.groupingBy(LeadAssignmentRelationDO::getSourceUserId));
        for (Long sourceUserId : sourceUserIds) {
            saveSourceRelations(scene.getCode(), sourceUserId, targetUserIds, mode,
                    existingMap.getOrDefault(sourceUserId, List.of()));
        }
        LeadAssignmentRelationLogDO log = new LeadAssignmentRelationLogDO();
        log.setScene(scene.getCode());
        log.setSourceUserIds(joinIds(sourceUserIds));
        log.setTargetUserIds(joinIds(targetUserIds));
        log.setActionType(mode);
        log.setOperatorUserId(operatorUserId);
        relationLogMapper.insert(log);
    }

    @Override
    public PageResult<LeadAssignmentLogRespVO> getLogPage(LeadAssignmentLogPageReqVO reqVO,
                                                          Long operatorUserId) {
        reqVO.setScene(SCENE);
        PageResult<LeadAssignmentRelationLogDO> page = getVisibleLogPage(reqVO, operatorUserId);
        return convertLogPage(page);
    }

    @Override
    public PageResult<UserRelationRespVO> getAdminRelationPage(UserRelationPageReqVO reqVO) {
        UserRelationSceneDO scene = sceneService.getSceneByCode(reqVO.getSceneCode());
        LeadAssignmentRelationPageReqVO relationReq = new LeadAssignmentRelationPageReqVO();
        relationReq.setPageNo(reqVO.getPageNo());
        relationReq.setPageSize(reqVO.getPageSize());
        relationReq.setKeyword(reqVO.getKeyword());
        relationReq.setDeptId(reqVO.getDeptId());
        relationReq.setConfigured(reqVO.getConfigured());
        PageResult<LeadAssignmentRelationRespVO> page = getRelationPageInternal(
                relationReq, null, scene, false);
        List<UserRelationRespVO> list = page.getList().stream().map(source -> {
            UserRelationRespVO result = new UserRelationRespVO();
            result.setId(source.getId());
            result.setNickname(source.getNickname());
            result.setMaskedMobile(source.getMaskedMobile());
            result.setDeptId(source.getDeptId());
            result.setDeptName(source.getDeptName());
            result.setAvatar(source.getAvatar());
            result.setStatus(source.getStatus());
            result.setTargetUsers(source.getSalesUsers());
            result.setValidTargetCount(source.getValidSalesCount());
            result.setInvalidTargetCount(source.getInvalidSalesCount());
            result.setUpdateTime(source.getUpdateTime());
            return result;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public List<LeadAssignmentUserRespVO> getAdminEligibleTargetUsers(String sceneCode) {
        return getEligibleTargetUsers(sceneService.getSceneByCode(sceneCode));
    }

    @Override
    public List<LeadAssignmentUserRespVO> getConfiguredTargetUsers(String sceneCode, Long sourceUserId) {
        UserRelationSceneDO scene = sceneService.getEnabledSceneByCode(sceneCode);
        Set<Long> configuredIds = relationMapper.selectListBySourceUserIds(sceneCode, Set.of(sourceUserId)).stream()
                .filter(this::isEnabledRelation).map(LeadAssignmentRelationDO::getTargetUserId)
                .collect(Collectors.toSet());
        List<AdminUserRespDTO> users = getEligibleTargetUsersInternal(scene).stream()
                .filter(user -> configuredIds.contains(user.getId()))
                .sorted(userComparator()).toList();
        Map<Long, DeptRespDTO> deptMap = getDeptMap(users, List.of());
        return users.stream().map(user -> convertUser(user, deptMap)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAdminRelations(UserRelationSaveReqVO reqVO, Long operatorUserId) {
        UserRelationSceneDO scene = sceneService.getEnabledSceneByCode(reqVO.getSceneCode());
        saveRelationsInternal(reqVO.getSourceUserIds(), reqVO.getTargetUserIds(), reqVO.getMode(),
                operatorUserId, scene, false, false);
    }

    @Override
    public PageResult<LeadAssignmentLogRespVO> getAdminLogPage(LeadAssignmentLogPageReqVO reqVO) {
        sceneService.getSceneByCode(reqVO.getScene());
        return convertLogPage(relationLogMapper.selectPage(reqVO));
    }

    private PageResult<LeadAssignmentLogRespVO> convertLogPage(
            PageResult<LeadAssignmentRelationLogDO> page) {
        Set<Long> userIds = page.getList().stream()
                .flatMap(log -> {
                    Set<Long> ids = parseIds(log.getSourceUserIds());
                    ids.addAll(parseIds(log.getTargetUserIds()));
                    ids.add(log.getOperatorUserId());
                    return ids.stream();
                }).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        List<LeadAssignmentLogRespVO> list = page.getList().stream().map(log -> {
            LeadAssignmentLogRespVO result = new LeadAssignmentLogRespVO();
            result.setId(log.getId());
            result.setSourceUsers(joinNames(log.getSourceUserIds(), userMap));
            result.setTargetUsers(joinNames(log.getTargetUserIds(), userMap));
            result.setActionType(log.getActionType());
            result.setOperatorUserId(log.getOperatorUserId());
            AdminUserRespDTO operator = userMap.get(log.getOperatorUserId());
            result.setOperatorName(operator != null ? operator.getNickname() : String.valueOf(log.getOperatorUserId()));
            result.setCreateTime(log.getCreateTime());
            return result;
        }).toList();
        return new PageResult<>(list, page.getTotal());
    }

    private PageResult<LeadAssignmentRelationLogDO> getVisibleLogPage(
            LeadAssignmentLogPageReqVO reqVO, Long operatorUserId) {
        if (permissionApi.hasAnyPermissions(operatorUserId, PERMISSION_MANAGE_ALL)) {
            return relationLogMapper.selectPage(reqVO);
        }
        Set<Long> managedDeptIds = getManagedDeptIds(operatorUserId);
        if (managedDeptIds.isEmpty()) {
            return PageResult.empty();
        }
        List<LeadAssignmentRelationLogDO> logs = relationLogMapper.selectList(reqVO);
        Set<Long> sourceUserIds = logs.stream()
                .flatMap(log -> parseIds(log.getSourceUserIds()).stream())
                .collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> sourceUserMap = adminUserApi.getUserMap(sourceUserIds);
        List<LeadAssignmentRelationLogDO> visibleLogs = logs.stream()
                .filter(log -> {
                    Set<Long> ids = parseIds(log.getSourceUserIds());
                    return CollUtil.isNotEmpty(ids) && ids.stream().allMatch(id -> {
                        AdminUserRespDTO user = sourceUserMap.get(id);
                        return user != null && managedDeptIds.contains(user.getDeptId());
                    });
                }).toList();
        long total = visibleLogs.size();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), visibleLogs.size());
        int to = Math.min(from + reqVO.getPageSize(), visibleLogs.size());
        return new PageResult<>(visibleLogs.subList(from, to), total);
    }

    private void saveSourceRelations(String scene, Long sourceUserId,
                                     Set<Long> requestedTargetIds, String mode,
                                     List<LeadAssignmentRelationDO> existing) {
        Set<Long> activeIds = existing.stream().filter(this::isEnabledRelation)
                .map(LeadAssignmentRelationDO::getTargetUserId).collect(Collectors.toSet());
        Set<Long> desiredIds = new HashSet<>(activeIds);
        if (MODE_APPEND.equals(mode)) {
            desiredIds.addAll(requestedTargetIds);
        } else if (MODE_REPLACE.equals(mode)) {
            desiredIds = new HashSet<>(requestedTargetIds);
        } else {
            desiredIds.removeAll(requestedTargetIds);
        }
        Map<Long, LeadAssignmentRelationDO> existingByTarget = existing.stream()
                .collect(Collectors.toMap(LeadAssignmentRelationDO::getTargetUserId, Function.identity()));
        for (LeadAssignmentRelationDO relation : existing) {
            int status = desiredIds.contains(relation.getTargetUserId())
                    ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus();
            if (!Objects.equals(status, relation.getStatus())) {
                relation.setStatus(status);
                relationMapper.updateById(relation);
            }
        }
        for (Long targetUserId : desiredIds) {
            if (existingByTarget.containsKey(targetUserId)) {
                continue;
            }
            LeadAssignmentRelationDO relation = new LeadAssignmentRelationDO();
            relation.setScene(scene);
            relation.setSourceUserId(sourceUserId);
            relation.setTargetUserId(targetUserId);
            relation.setStatus(CommonStatusEnum.ENABLE.getStatus());
            relationMapper.insert(relation);
        }
    }

    private LeadAssignmentRelationRespVO buildRelationResp(AdminUserRespDTO sourceUser,
                                                            List<LeadAssignmentRelationDO> relations,
                                                            Map<Long, AdminUserRespDTO> targetUserMap,
                                                            Set<Long> validSalesIds,
                                                            Map<Long, DeptRespDTO> deptMap) {
        LeadAssignmentRelationRespVO result = new LeadAssignmentRelationRespVO();
        copyUser(result, sourceUser, deptMap);
        List<LeadAssignmentUserRespVO> salesUsers = relations.stream()
                .map(LeadAssignmentRelationDO::getTargetUserId)
                .map(targetUserMap::get).filter(Objects::nonNull)
                .sorted(userComparator()).map(user -> convertUser(user, deptMap)).toList();
        result.setSalesUsers(salesUsers);
        int validCount = (int) relations.stream().map(LeadAssignmentRelationDO::getTargetUserId)
                .filter(validSalesIds::contains).count();
        result.setValidSalesCount(validCount);
        result.setInvalidSalesCount(relations.size() - validCount);
        result.setUpdateTime(relations.stream().map(LeadAssignmentRelationDO::getUpdateTime)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null));
        return result;
    }

    private Map<Long, List<LeadAssignmentRelationDO>> getActiveRelationMap(
            String scene, Collection<Long> sourceUserIds) {
        return relationMapper.selectListBySourceUserIds(scene, sourceUserIds).stream()
                .filter(this::isEnabledRelation)
                .collect(Collectors.groupingBy(LeadAssignmentRelationDO::getSourceUserId));
    }

    private boolean isEnabledRelation(LeadAssignmentRelationDO relation) {
        return CommonStatusEnum.ENABLE.getStatus().equals(relation.getStatus());
    }

    @Override
    public Set<Long> getActiveTargetUserIds(String sceneCode, Long sourceUserId) {
        if (sourceUserId == null) return Set.of();
        return relationMapper.selectListBySourceUserIds(sceneCode, Set.of(sourceUserId)).stream()
                .filter(this::isEnabledRelation).map(LeadAssignmentRelationDO::getTargetUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<AdminUserRespDTO> getUsersByPostCode(String postCode, boolean enabledOnly) {
        PostRespDTO post = postApi.getPostByCode(postCode);
        if (post == null || !CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())) {
            return List.of();
        }
        return adminUserApi.getUserListByPostIds(Collections.singleton(post.getId())).stream()
                .filter(user -> !enabledOnly || CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .toList();
    }

    private List<AdminUserRespDTO> getEligibleTargetUsersInternal(UserRelationSceneDO scene) {
        if ("permission".equals(scene.getTargetEligibilityType())) {
            Set<Long> userIds = permissionApi.getEnabledUserIdsByPermission(scene.getTargetPermissionCode());
            return adminUserApi.getUserList(userIds).stream()
                    .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                    .toList();
        }
        return getUsersByPostCode(scene.getTargetPostCode(), true);
    }

    private boolean canManageSourceUser(Long operatorUserId, AdminUserRespDTO sourceUser) {
        if (permissionApi.hasAnyPermissions(operatorUserId, PERMISSION_MANAGE_ALL)) {
            return true;
        }
        return getManagedDeptIds(operatorUserId).contains(sourceUser.getDeptId());
    }

    private Set<Long> getManagedDeptIds(Long operatorUserId) {
        Set<Long> deptIds = new HashSet<>();
        for (DeptRespDTO dept : deptApi.getDeptListByLeaderUserId(operatorUserId)) {
            deptIds.add(dept.getId());
            deptIds.addAll(deptApi.getChildDeptList(dept.getId()).stream()
                    .map(DeptRespDTO::getId).toList());
        }
        return deptIds;
    }

    private Map<Long, DeptRespDTO> getDeptMap(Collection<AdminUserRespDTO> first,
                                               Collection<AdminUserRespDTO> second) {
        Set<Long> deptIds = new HashSet<>();
        first.stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull).forEach(deptIds::add);
        second.stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull).forEach(deptIds::add);
        return deptApi.getDeptMap(deptIds);
    }

    private static LeadAssignmentUserRespVO convertUser(AdminUserRespDTO user,
                                                         Map<Long, DeptRespDTO> deptMap) {
        LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
        copyUser(result, user, deptMap);
        return result;
    }

    private static void copyUser(LeadAssignmentUserRespVO result, AdminUserRespDTO user,
                                 Map<Long, DeptRespDTO> deptMap) {
        result.setId(user.getId());
        result.setNickname(user.getNickname());
        result.setMaskedMobile(user.getMobile());
        result.setDeptId(user.getDeptId());
        DeptRespDTO dept = deptMap.get(user.getDeptId());
        result.setDeptName(dept != null ? dept.getName() : null);
        result.setAvatar(user.getAvatar());
        result.setStatus(user.getStatus());
    }

    private static boolean matchesKeyword(AdminUserRespDTO user, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return true;
        }
        return StrUtil.containsIgnoreCase(user.getNickname(), keyword)
                || StrUtil.contains(user.getMobile(), keyword);
    }

    private static Comparator<AdminUserRespDTO> userComparator() {
        return Comparator.comparing(AdminUserRespDTO::getNickname,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(AdminUserRespDTO::getId);
    }

    private static String joinIds(Collection<Long> ids) {
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private static Set<Long> parseIds(String ids) {
        if (StrUtil.isBlank(ids)) {
            return new HashSet<>();
        }
        return Arrays.stream(ids.split(",")).map(Long::valueOf).collect(Collectors.toSet());
    }

    private static String joinNames(String ids, Map<Long, AdminUserRespDTO> userMap) {
        return parseIds(ids).stream().map(id -> {
            AdminUserRespDTO user = userMap.get(id);
            return user != null ? user.getNickname() : String.valueOf(id);
        }).sorted().collect(Collectors.joining("、"));
    }

}
