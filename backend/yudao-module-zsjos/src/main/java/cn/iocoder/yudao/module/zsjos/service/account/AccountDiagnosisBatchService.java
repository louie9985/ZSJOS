package cn.iocoder.yudao.module.zsjos.service.account;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.AccountDiagnosisBatchVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountProfileVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountProfileEntryMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class AccountDiagnosisBatchService {
    @Resource private MediaAccountMapper accounts;
    @Resource private MediaAccountObjectPermissionProvider objects;
    @Resource private MediaAccountProfileService profiles;
    @Resource private WeeklyDiagnosisService weekly;
    @Resource private MediaAccountProfileEntryMapper entries;

    private MediaAccountDO requireAccount(Long id, Long user) {
        var account = accounts.selectById(id);
        if (account == null || !Objects.equals(account.getDirectorUserId(), user)
                || !objects.hasPermission(id, "edit", user)) throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
        return account;
    }

    private void sameService(MediaAccountDO anchor, MediaAccountDO target) {
        if (anchor.getStudentPersonId() == null || anchor.getCreateServiceRelationId() == null
                || !Objects.equals(anchor.getStudentPersonId(), target.getStudentPersonId())
                || !Objects.equals(anchor.getCreateServiceRelationId(), target.getCreateServiceRelationId()))
            throw exception(MEDIA_ACCOUNT_DIAGNOSIS_BATCH_SCOPE_INVALID);
    }

    private boolean initial(String type) {
        if (!Set.of("diagnosis_initial", "weekly").contains(Objects.toString(type, "")))
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        return "diagnosis_initial".equals(type);
    }

    @ZsjosPermission(bizType = "media-account", bizId = "#accountId", action = "edit")
    public List<Candidate> candidates(Long accountId, String templateType, String nodeKey, Long user) {
        boolean initial = initial(templateType);
        var anchor = requireAccount(accountId, user);
        sameService(anchor, anchor);
        var result = new ArrayList<Candidate>();
        for (var account : accounts.selectList(new LambdaQueryWrapper<MediaAccountDO>()
                .eq(MediaAccountDO::getStudentPersonId, anchor.getStudentPersonId())
                .eq(MediaAccountDO::getCreateServiceRelationId, anchor.getCreateServiceRelationId())
                .eq(MediaAccountDO::getDirectorUserId, user).orderByAsc(MediaAccountDO::getId))) {
            // Scope is authoritative on the server, including before returning candidate identities.
            if (!Objects.equals(account.getDirectorUserId(), user) || "deleted".equals(account.getRunStatus())
                    || !objects.hasPermission(account.getId(), "edit", user)
                    || !objects.hasPermission(account.getId(), "read", user)) continue;
            sameService(anchor, account);
            var profile = profiles.get(account.getId(), user);
            String reason = null, key = null;
            if (initial) {
                if (!Boolean.TRUE.equals(profile.isCanStartDiagnosis())) reason = Boolean.TRUE.equals(profile.isDiagnosisStarted())
                        ? "已完成启动诊断" : "尚未应用有效定位卡或账号不可维护";
            } else if (!Boolean.TRUE.equals(profile.isCanSubmitDiagnosis())) reason = "请先完成启动诊断或确认账号维护资格";
            else {
                var context = MediaAccountDiagnosisScheduler.context(anchor);
                var node = weekly.overview(account.getId(), user).nodes().stream()
                        .filter(n -> n.roundKey().equals(Objects.toString(context.get("roundKey"), ""))
                                && Objects.equals(nodeKey, WeeklyDiagnosisPeriods.key(accountId, n.roundKey(), n.dueAt())))
                        .findFirst().orElse(null);
                if (node == null) reason = "本节点已填写、尚未开放或定位轮次不同";
                else key = node.key();
            }
            result.add(new Candidate(account.getId(), account.getAccountNo(), account.getNickname(), account.getPlatformLabelSnapshot(),
                    account.getVersion(), profile.getConfig().getId(), account.getSStage(), account.getCurrentStatusValue(), key, reason == null, reason));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "media-account", bizId = "#request.accountId", action = "edit")
    public List<Result> submit(Submit request, Long user) {
        boolean initial = initial(request.getTemplateType());
        if (request.getAccounts() == null || request.getAccounts().isEmpty() || request.getAccounts().size() > 100
                || request.getAccounts().stream().anyMatch(i -> i == null || i.getAccountId() == null)
                || request.getAccounts().stream().map(Item::getAccountId).distinct().count() != request.getAccounts().size())
            throw exception(MEDIA_ACCOUNT_DIAGNOSIS_BATCH_SELECTION_INVALID);
        var sorted = request.getAccounts().stream().sorted(Comparator.comparing(Item::getAccountId)).toList();
        // All targets are locked and authorized before the first mutation, using a stable lock order.
        var locked = new LinkedHashMap<Long, MediaAccountDO>();
        var ids = new TreeSet<Long>(); ids.add(request.getAccountId()); sorted.forEach(i -> ids.add(i.getAccountId()));
        for (Long id : ids) {
            var account = accounts.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
            if (account == null || !Objects.equals(account.getDirectorUserId(), user)
                    || "deleted".equals(account.getRunStatus()) || !objects.hasPermission(id, "edit", user))
                throw exception(MEDIA_ACCOUNT_PERMISSION_DENIED);
            locked.put(id, account);
        }
        var anchor = locked.get(request.getAccountId());
        for (var item : sorted) {
            sameService(anchor, locked.get(item.getAccountId()));
            if (initial ? item.getInitial() == null || item.getWeekly() != null
                    || !"diagnosis_initial".equals(item.getInitial().getTemplateType()) || item.getInitial().getPreviousEntryId() != null
                    : item.getWeekly() == null || item.getInitial() != null || item.getWeekly().getCommon() == null
                    || item.getWeekly().getCommon().getPreviousEntryId() != null)
                throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        var replayed = new ArrayList<Result>();
        for (var item : sorted) {
            var replay = entries.replay(item.getAccountId(), user, key(request, item));
            if (replay == null) continue;
            var content = JsonUtils.parseObject(replay.getContent(), Map.class);
            if (!Objects.equals(fingerprint, content.get("diagnosisBatchFingerprint")))
                throw exception(MEDIA_ACCOUNT_PROFILE_IDEMPOTENCY_CONFLICT);
            replayed.add(new Result(item.getAccountId(), replay.getResultVersion()));
        }
        if (!replayed.isEmpty()) {
            if (replayed.size() != sorted.size()) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            return replayed;
        }
        // Recheck every node before any submission. A stale or foreign node cannot partially complete a batch.
        if (!initial) for (var item : sorted) {
            var node = weekly.overview(item.getAccountId(), user).nodes().stream()
                    .filter(n -> Objects.equals(n.key(), item.getWeekly().getNodeKey())).findFirst()
                    .orElseThrow(() -> exception(MEDIA_ACCOUNT_DIAGNOSIS_TASK_UNAVAILABLE));
            var context = MediaAccountDiagnosisScheduler.context(anchor);
            if (!Objects.equals(node.roundKey(), Objects.toString(context.get("roundKey"), ""))
                    || !Objects.equals(request.getNodeKey(), WeeklyDiagnosisPeriods.key(request.getAccountId(), node.roundKey(), node.dueAt())))
                throw exception(MEDIA_ACCOUNT_DIAGNOSIS_BATCH_SCOPE_INVALID);
        }
        var result = new ArrayList<Result>();
        for (var item : sorted) {
            // Downstream services normalize their request; clone it to keep retries and batch fingerprints stable.
            var common = JsonUtils.parseObject(JsonUtils.toJsonString(initial ? item.getInitial() : item.getWeekly().getCommon()), MediaAccountProfileVO.DiagnosisRequest.class);
            common.setIdempotencyKey(key(request, item));
            Integer version;
            if (initial) version = profiles.submitDiagnosis(item.getAccountId(), common, user);
            else {
                var command = JsonUtils.parseObject(JsonUtils.toJsonString(item.getWeekly()), cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Submit.class);
                command.setCommon(common);
                version = weekly.submit(item.getAccountId(), command, user);
            }
            var saved = entries.replay(item.getAccountId(), user, common.getIdempotencyKey());
            if (saved == null) throw exception(MEDIA_ACCOUNT_VERSION_CONFLICT);
            var content = JsonUtils.parseObject(saved.getContent(), Map.class);
            content.put("diagnosisBatchFingerprint", fingerprint);
            content.put("diagnosisBatchKey", request.getIdempotencyKey());
            content.put("diagnosisBatchAccountIds", sorted.stream().map(Item::getAccountId).toList());
            saved.setContent(JsonUtils.toJsonString(content)); entries.updateById(saved);
            result.add(new Result(item.getAccountId(), version));
        }
        return result;
    }

    private String key(Submit request, Item item) {
        return DigestUtil.sha256Hex("diagnosis-batch:" + request.getIdempotencyKey() + ":" + item.getAccountId());
    }
}
