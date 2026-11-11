package cn.iocoder.yudao.module.zsjos.service.versionupdate;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.versionupdate.vo.VersionUpdateVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.versionupdate.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.versionupdate.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.service.versionupdate.VersionUpdateConstants.*;

@Service
public class VersionUpdateService {
    @Resource private VersionUpdateMapper updates;
    @Resource private VersionUpdateRecipientMapper recipients;
    @Resource private VersionUpdatePermissionProvider objects;
    @Resource private AdminUserApi users;
    @Resource private DeptApi departments;
    @Resource private PermissionApi permissions;
    @Resource private FileApi files;
    @Resource private XssCleaner cleaner;
    @Resource private WebSocketSenderApi sockets;
    private static final Set<String> EXTENSIONS = Set.of("png","jpg","jpeg","gif","webp","pdf","doc","docx","xls","xlsx","ppt","pptx","zip");

    private void management(String action) {
        if (!permissions.hasAnyPermissions(getLoginUserId(), QUERY)
                || !permissions.hasAnyPermissions(getLoginUserId(), PREFIX + action)) throw exception(DENIED);
    }
    private void reader() {
        if (!permissions.hasAnyPermissions(getLoginUserId(), READ)) throw exception(DENIED);
    }
    private VersionUpdateDO require(Long id, boolean lock) {
        var row = lock ? updates.lock(id) : updates.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())) throw exception(NOT_FOUND);
        return row;
    }
    private void state(VersionUpdateDO row, String expected) {
        if (!expected.equals(row.getStatus())) throw exception(STATE);
    }
    private Set<Long> eligible() {
        var ids = new HashSet<>(permissions.getEnabledUserIdsByPermission(READ));
        ids.retainAll(permissions.getEnabledUserIdsByPermission(ACKNOWLEDGE));
        return ids;
    }
    private List<AdminUserRespDTO> validateRecipients(List<Long> ids) {
        var unique = new LinkedHashSet<>(ids);
        if (unique.isEmpty() || unique.contains(null) || !eligible().containsAll(unique)) throw exception(RECIPIENT);
        var result = users.getUserList(unique);
        if (result.size() != unique.size() || result.stream().anyMatch(user -> !Integer.valueOf(0).equals(user.getStatus()))) throw exception(RECIPIENT);
        return result;
    }
    public Options options() {
        management("query");
        var people = users.getUserListByStatus(0);
        var allowed = eligible();
        Map<Long, DeptRespDTO> tree = new LinkedHashMap<>();
        var deptIds = people.stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!deptIds.isEmpty()) {
            for (var dept : departments.getDeptList(deptIds)) {
                tree.put(dept.getId(), dept);
                departments.getParentDeptList(dept.getId()).forEach(parent -> tree.put(parent.getId(), parent));
            }
        }
        return new Options(tree.values().stream().map(dept -> new Department(dept.getId(), dept.getName(), dept.getParentId())).toList(),
                people.stream().map(user -> new User(user.getId(), user.getNickname(), user.getDeptId(), allowed.contains(user.getId()))).toList());
    }
    @Transactional
    public Long create(Save request) {
        management("create");
        var row = new VersionUpdateDO();
        row.setTenantId(TenantContextHolder.getRequiredTenantId());
        apply(row, request, Set.of());
        row.setStatus("DRAFT");
        updates.insert(row);
        return row.getId();
    }
    @Transactional
    @ZsjosPermission(bizType="version-update", bizId="#id", action="update")
    public void update(Long id, Save request) {
        objects.check(id, "update", getLoginUserId());
        var row = require(id, true);
        state(row, "DRAFT");
        var previousFiles = new HashSet<>(ids(row.getAttachmentIds()));
        previousFiles.addAll(ids(row.getInlineFileIds()));
        apply(row, request, previousFiles);
        updates.updateById(row);
    }
    private void apply(VersionUpdateDO row, Save request, Set<Long> previousFiles) {
        validateRecipients(request.getTargetUserIds());
        var document = Jsoup.parseBodyFragment(request.getContent());
        Map<String, Long> inlineUrls = new HashMap<>();
        request.getInlineFiles().forEach(file -> {
            if (file.getId() == null || file.getUrl() == null) throw exception(FILE);
            inlineUrls.put(file.getUrl(), file.getId());
        });
        var inlineIds = new LinkedHashSet<Long>();
        for (var image : document.select("img")) {
            Long fileId = inlineUrls.get(image.attr("src"));
            if (fileId == null && image.hasAttr("data-version-file")) {
                try { fileId = Long.valueOf(image.attr("data-version-file")); }
                catch (NumberFormatException cause) { throw exception(FILE); }
            }
            if (fileId == null) throw exception(FILE);
            inlineIds.add(fileId);
            image.attr("src", "https://version-update.invalid/file/" + fileId);
            image.removeAttr("data-version-file");
        }
        String content = cleaner.clean(document.body().html());
        if (Jsoup.parse(content).text().isBlank() && !content.contains("<img")) throw exception(CONTENT);
        var attachments = new LinkedHashSet<>(request.getAttachmentIds());
        var allFiles = new LinkedHashSet<>(attachments);
        allFiles.addAll(inlineIds);
        for (Long fileId : allFiles) {
            var file = files.getFileInfo(fileId);
            if (file == null || (!previousFiles.contains(fileId) &&
                    (!Objects.equals(file.getCreator(), String.valueOf(getLoginUserId()))
                            || file.getPath() == null || !file.getPath().startsWith("zsjos/version-update/" + getLoginUserId() + "/")))) throw exception(FILE);
        }
        row.setTitle(request.getTitle().trim());
        row.setContent(content);
        row.setTargetUserIds(JSONUtil.toJsonStr(new LinkedHashSet<>(request.getTargetUserIds())));
        row.setAttachmentIds(JSONUtil.toJsonStr(attachments));
        row.setInlineFileIds(JSONUtil.toJsonStr(inlineIds));
    }
    @Transactional
    @ZsjosPermission(bizType="version-update", bizId="#id", action="publish")
    public void publish(Long id) {
        objects.check(id, "publish", getLoginUserId());
        var row = require(id, true);
        if ("PUBLISHED".equals(row.getStatus())) return;
        state(row, "DRAFT");
        var people = validateRecipients(ids(row.getTargetUserIds()));
        var deptIds = people.stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        var deptMap = deptIds.isEmpty() ? Map.<Long, DeptRespDTO>of() : departments.getDeptMap(deptIds);
        for (var user : people) {
            var recipient = new VersionUpdateRecipientDO();
            recipient.setUpdateId(id); recipient.setUserId(user.getId()); recipient.setUserName(user.getNickname());
            recipient.setDeptId(user.getDeptId());
            var dept = deptMap.get(user.getDeptId());
            recipient.setDeptName(dept == null ? null : dept.getName());
            recipients.insert(recipient);
        }
        var publisher = users.getUser(getLoginUserId());
        row.setPublisherId(getLoginUserId());
        row.setPublisherName(publisher == null ? null : publisher.getNickname());
        row.setPublishTime(LocalDateTime.now());
        row.setStatus("PUBLISHED");
        updates.updateById(row);
        hint();
    }
    @Transactional
    @ZsjosPermission(bizType="version-update", bizId="#id", action="offline")
    public void offline(Long id) {
        objects.check(id, "offline", getLoginUserId());
        var row = require(id, true);
        if ("OFFLINE".equals(row.getStatus())) return;
        state(row, "PUBLISHED");
        row.setStatus("OFFLINE");
        updates.updateById(row);
        hint();
    }
    public PageResult<Item> page(Query query) {
        management("query");
        var result = updates.page(query, null);
        return new PageResult<>(result.getList().stream().map(row -> item(row, false, true)).toList(), result.getTotal());
    }
    public PageResult<Item> mine(Query query) {
        reader();
        var result = updates.page(query, getLoginUserId());
        return new PageResult<>(result.getList().stream().map(row -> item(row, false, false)).toList(), result.getTotal());
    }
    @ZsjosPermission(bizType="version-update", bizId="#id", action="query")
    public Item detail(Long id) {
        objects.check(id, "query", getLoginUserId());
        return item(require(id, false), true, true);
    }
    @ZsjosPermission(bizType="version-update", bizId="#id", action="read")
    public Item myDetail(Long id) {
        objects.check(id, "read", getLoginUserId());
        var row = require(id, false);
        state(row, "PUBLISHED");
        return item(row, true, false);
    }
    @Transactional
    @ZsjosPermission(bizType="version-update", bizId="#id", action="acknowledge")
    public void acknowledge(Long id) {
        objects.check(id, "acknowledge", getLoginUserId());
        state(require(id, true), "PUBLISHED");
        // Row lock serializes offline with acknowledgement. Conditional update retains first acknowledgement.
        recipients.update(null, new LambdaUpdateWrapper<VersionUpdateRecipientDO>()
                .eq(VersionUpdateRecipientDO::getUpdateId, id).eq(VersionUpdateRecipientDO::getUserId, getLoginUserId())
                .isNull(VersionUpdateRecipientDO::getAcknowledgedTime)
                .set(VersionUpdateRecipientDO::getAcknowledgedTime, LocalDateTime.now()));
        hint();
    }
    @ZsjosPermission(bizType="version-update", bizId="#id", action="query")
    public PageResult<Recipient> recipients(Long id, Query query) {
        objects.check(id, "query", getLoginUserId());
        var wrapper = new LambdaQueryWrapper<VersionUpdateRecipientDO>().eq(VersionUpdateRecipientDO::getUpdateId, id)
                .isNull(Boolean.TRUE.equals(query.getPending()), VersionUpdateRecipientDO::getAcknowledgedTime)
                .orderByAsc(VersionUpdateRecipientDO::getId);
        var result = recipients.selectPage(query, wrapper);
        return new PageResult<>(BeanUtils.toBean(result.getList(), Recipient.class), result.getTotal());
    }
    public Attachment upload(MultipartFile file) throws Exception {
        management("query");
        if (!permissions.hasAnyPermissions(getLoginUserId(), PREFIX + "create", PREFIX + "update")) throw exception(DENIED);
        String name = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (file.isEmpty() || file.getSize() > 100L * 1024 * 1024 || !EXTENSIONS.contains(extension)) throw exception(FILE);
        var saved = files.createFileInfo(file.getBytes(), name, "zsjos/version-update/" + getLoginUserId(), file.getContentType());
        return attachment(saved.getId());
    }
    private Attachment attachment(Long id) {
        var file = files.getFileInfo(id);
        var result = new Attachment();
        result.setId(id);
        if (file == null) {
            result.setName("附件已不可用");
            return result;
        }
        result.setName(file.getName()); result.setType(file.getType()); result.setSize(file.getSize());
        try { result.setUrl(files.presignGetUrl(id, 600)); }
        catch (RuntimeException ignored) { /* Text and acknowledgement remain available during file-storage failures. */ }
        return result;
    }
    private List<Long> ids(String json) { return json == null ? List.of() : JSONUtil.parseArray(json).toList(Long.class); }
    private Item item(VersionUpdateDO row, boolean content, boolean management) {
        // Recipient projections expose only their own acknowledgement, never the selected roster.
        var result = new Item();
        result.setId(row.getId());
        result.setTitle(row.getTitle());
        result.setStatus(row.getStatus());
        result.setPublisherName(row.getPublisherName());
        result.setPublishTime(row.getPublishTime());
        if (content) result.setContent(row.getContent());
        if (content) {
            result.setAttachments(ids(row.getAttachmentIds()).stream().map(this::attachment).toList());
            var document = Jsoup.parseBodyFragment(row.getContent());
            for (Long fileId : ids(row.getInlineFileIds())) {
                for (var image : document.select("img")) {
                    if (image.attr("src").equals("https://version-update.invalid/file/" + fileId)) {
                        try { image.attr("src", files.presignGetUrl(fileId, 600)); }
                        catch (RuntimeException ignored) { image.removeAttr("src"); image.attr("alt", "图片暂不可用"); }
                        image.attr("data-version-file", fileId.toString());
                    }
                }
            }
            result.setContent(document.body().html());
        }
        if (management) {
            if (content) result.setTargetUserIds(ids(row.getTargetUserIds()));
            result.setRecipientCount(recipients.selectCount(new LambdaQueryWrapper<VersionUpdateRecipientDO>().eq(VersionUpdateRecipientDO::getUpdateId, row.getId())));
            result.setAcknowledgedCount(recipients.selectCount(new LambdaQueryWrapper<VersionUpdateRecipientDO>().eq(VersionUpdateRecipientDO::getUpdateId, row.getId()).isNotNull(VersionUpdateRecipientDO::getAcknowledgedTime)));
        } else {
            var recipient = recipients.find(row.getId(), getLoginUserId());
            result.setAcknowledgedTime(recipient == null ? null : recipient.getAcknowledgedTime());
        }
        return result;
    }
    private void hint() {
        // Database state remains authoritative if realtime delivery is temporarily unavailable.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { sockets.sendObject(cn.iocoder.yudao.framework.common.enums.UserTypeEnum.ADMIN.getValue(), "version-update-changed", Map.of()); }
                catch (RuntimeException ignored) { /* Login, focus and periodic queries recover missed hints. */ }
            }
        });
    }
}
