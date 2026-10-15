package cn.iocoder.yudao.module.pms.service.kb.importing;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.controller.admin.kb.importing.vo.PmsKnowledgeImportVO.*;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeDocumentCreateReqVO;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.folder.PmsKnowledgeFolderSaveReqVO;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.importing.*;
import cn.iocoder.yudao.module.pms.dal.dataobject.kb.content.*;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.importing.*;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.content.*;
import cn.iocoder.yudao.module.pms.dal.mysql.kb.library.PmsKnowledgeLibraryMapper;
import cn.iocoder.yudao.module.pms.service.kb.content.*;
import cn.iocoder.yudao.module.pms.service.kb.library.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.kb.importing.PmsKnowledgeImportErrors.*;

@Service
public class PmsKnowledgeImportService {
    @Resource private PmsKnowledgeImportPolicy policy;
    @Resource private PmsKnowledgeImportTaskMapper tasks;
    @Resource private PmsKnowledgeImportItemMapper items;
    @Resource private PmsKnowledgeLibraryMapper libraries;
    @Resource private PmsKnowledgeFolderMapper folders;
    @Resource private PmsKnowledgeDocumentMapper documents;
    @Resource private PmsKnowledgeLibraryService libraryService;
    @Resource private PmsKnowledgeLibraryMemberService members;
    @Resource private PmsKnowledgeContentPermissionService permissions;
    @Resource private PmsKnowledgeFolderService folderService;
    @Resource private PmsKnowledgeDocumentService documentService;
    @Resource private PermissionApi permissionApi;
    @Resource private FileApi fileApi;
    private final TransactionTemplate tx;
    public PmsKnowledgeImportService(org.springframework.transaction.PlatformTransactionManager manager) {
        this.tx = new TransactionTemplate(manager);
    }

    public PmsKnowledgeImportPolicy policy() { return policy; }

    private void feature(Long user, String permission) {
        if (!permissionApi.hasAnyPermissions(user, permission)) throw exception(DENIED);
    }
    private void writable(Long libraryId, Long folderId, Long user) {
        feature(user, "pms:kb:library:update");
        members.validateLibraryInteraction(libraryId, user);
        if (folderId == 0) { members.validateLibraryWritable(libraryId, user); return; }
        var folder = folders.selectById(folderId);
        if (folder == null || !Objects.equals(folder.getLibraryId(),libraryId)
                || !Objects.equals(folder.getStatus(),1)) throw exception(TARGET);
        permissions.validateContentPermissionWritable(folder.getPermissionId(), libraryId, user);
    }
    private PmsKnowledgeImportTaskDO owned(Long id, Long user, boolean lock) {
        var q = new LambdaQueryWrapperX<PmsKnowledgeImportTaskDO>().eq(PmsKnowledgeImportTaskDO::getId,id)
                .eq(PmsKnowledgeImportTaskDO::getOwnerId,user);
        if (lock) q.last("FOR UPDATE");
        var task = tasks.selectOne(q);
        if (task == null) throw exception(DENIED);
        return task;
    }
    private List<PmsKnowledgeImportItemDO> entries(Long taskId) {
        return items.selectList(new LambdaQueryWrapperX<PmsKnowledgeImportItemDO>()
                .eq(PmsKnowledgeImportItemDO::getTaskId,taskId).orderByAsc(PmsKnowledgeImportItemDO::getId));
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }

    public static String[] path(String value, String root, int depth) {
        if (value == null || value.length()>2048 || value.contains("\\") || value.contains(":"))
            throw exception(INVALID,"文件路径无效");
        String[] parts = value.split("/",-1);
        if (parts.length<2 || !parts[0].equals(root) || parts.length-1>depth)
            throw exception(INVALID,"文件路径或目录层级无效");
        for (String part:parts) if (part.isBlank() || part.equals(".") || part.equals("..")
                || part.length()>255 || part.chars().anyMatch(c -> c<32 || c==127))
            throw exception(INVALID,"文件路径无效");
        return parts;
    }
    public static String title(String fileName) {
        int dot=fileName.lastIndexOf('.');
        String title=dot<0?fileName:fileName.substring(0,dot);
        return title.isBlank()?fileName:title;
    }
    private String fileReason(Entry f) {
        if (f.getFileSize()==0) return "空文件";
        if (f.getFileSize()>policy.getMaxFileSize()) return "超过单文件大小限制";
        String name=f.getRelativePath(); int dot=name.lastIndexOf('.');
        if (dot<name.lastIndexOf('/') || !policy.getFileTypes().contains(name.substring(dot+1).toLowerCase(Locale.ROOT)))
            return "不支持的文件类型";
        if (f.getDigest()==null) throw exception(INVALID,"缺少文件内容摘要");
        return null;
    }
    private List<PmsKnowledgeImportItemDO> inspect(Request req, Long user) {
        feature(user,"pms:kb:library:update");
        boolean fresh="NEW".equals(req.getMode());
        if (fresh) {
            feature(user,"pms:kb:library:create");
            if (req.getLibrary()==null || req.getLibraryId()!=null || req.getFolderId()!=null
                    || req.getLibrary().getId()!=null || req.getLibrary().getTemplateId()!=null)
                throw exception(INVALID,"新建模式不能指定已有目标或模板");
        } else {
            if (!"EXISTING".equals(req.getMode()) || req.getLibrary()!=null || req.getLibraryId()==null
                    || req.getFolderId()==null || req.getFolderId()<0) throw exception(INVALID,"导入目标无效");
            writable(req.getLibraryId(),req.getFolderId(),user);
        }
        if(req.getFiles().size()>policy.getMaxFiles()) throw exception(INVALID,"文件数量超限，请拆分导入");
        long total=0;
        Set<String> paths=new HashSet<>();
        Set<String> targets=new HashSet<>();
        Set<String> directoryPaths=new HashSet<>();
        var sorted=req.getFiles().stream().sorted(Comparator.comparing(Entry::getRelativePath)).toList();
        for(var f:sorted) {
            path(f.getRelativePath(),req.getRootName(),policy.getMaxDepth());
            if(!paths.add(f.getRelativePath())) throw exception(INVALID,"文件清单存在重复路径");
            if(f.getFileSize()>policy.getMaxTotalSize()-total) throw exception(INVALID,"文件总大小超限，请拆分导入");
            total+=f.getFileSize();
            String dir=f.getRelativePath();
            while(dir.contains("/")) {dir=dir.substring(0,dir.lastIndexOf('/'));directoryPaths.add(dir.toLowerCase(Locale.ROOT));}
        }
        List<PmsKnowledgeImportItemDO> result=new ArrayList<>();
        for (var f:sorted) {
            String name=f.getRelativePath().substring(f.getRelativePath().lastIndexOf('/')+1);
            var item=BeanUtils.toBean(f,PmsKnowledgeImportItemDO.class);
            item.setPathHash(hash(f.getRelativePath())); item.setTitle(title(name));
            item.setFileType(name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT));
            item.setVersion(0); item.setStatus("PENDING");
            String reason=fileReason(f);
            String target=(f.getRelativePath().substring(0,f.getRelativePath().lastIndexOf('/')+1)+item.getTitle()).toLowerCase(Locale.ROOT);
            if(reason==null && directoryPaths.contains(target)) reason="文件与目录名称冲突";
            if(reason==null && !targets.add(target)) reason="本批次同名文件，已跳过";
            if(reason==null && !fresh) {
                try {
                    Long folder=resolve(req.getLibraryId(),req.getFolderId(),f.getRelativePath(),false,user,false);
                    if(folder!=null && collision(req.getLibraryId(),folder,item.getTitle())) reason="目标冲突或不可写";
                } catch(ServiceException e) {reason="目标冲突或不可写";}
            }
            if(reason!=null) {item.setStatus("SKIPPED");item.setReason(reason);}
            result.add(item);
        }
        return result;
    }
    public List<Item> prepare(Request req, Long user) {
        return BeanUtils.toBean(inspect(req,user),Item.class);
    }
    private PmsKnowledgeImportTaskDO byKey(String key, Long user) {
        return tasks.selectOne(new LambdaQueryWrapperX<PmsKnowledgeImportTaskDO>()
                .eq(PmsKnowledgeImportTaskDO::getOwnerId,user).eq(PmsKnowledgeImportTaskDO::getRequestKey,key));
    }
    public Task create(Request req, Long user) {
        // Canonicalize the immutable manifest; the request key cannot silently acquire new contents.
        String manifest=hash(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(req));
        var existing=byKey(req.getRequestKey(),user);
        if(existing!=null) {
            if(!manifest.equals(existing.getManifestHash())) throw exception(INVALID,"请求标识已用于不同导入内容");
            return get(existing.getId(),user);
        }
        Long id;
        try {
            id=tx.execute(status -> {
                if(req.getLibraryId()!=null) libraries.lockContentCreation(req.getLibraryId());
                var checked=inspect(req,user);
                if(checked.stream().noneMatch(i->"PENDING".equals(i.getStatus()))) throw exception(INVALID,"没有可导入文件");
                var task=new PmsKnowledgeImportTaskDO();
                task.setOwnerId(user);task.setRootName(req.getRootName());task.setRequestKey(req.getRequestKey());
                task.setMode(req.getMode());task.setManifestHash(manifest);task.setStatus("OPEN");
                boolean fresh="NEW".equals(req.getMode());
                task.setLibraryId(fresh?libraryService.createLibrary(req.getLibrary(),user):req.getLibraryId());
                task.setFolderId(fresh?0L:req.getFolderId());tasks.insert(task);
                for(var item:checked) {item.setTaskId(task.getId());items.insert(item);}
                return task.getId();
            });
        } catch(DuplicateKeyException e) {
            existing=byKey(req.getRequestKey(),user);
            if(existing==null || !manifest.equals(existing.getManifestHash())) throw e;
            id=existing.getId();
        }
        return get(id,user);
    }
    public Task get(Long id,Long user) {
        var task=owned(id,user,false);
        var list=entries(id); var result=BeanUtils.toBean(task,Task.class);
        result.setTotal(list.size());result.setSuccess(list.stream().filter(i->"SUCCESS".equals(i.getStatus())).count());
        result.setSkipped(list.stream().filter(i->"SKIPPED".equals(i.getStatus())).count());
        result.setFailed(list.stream().filter(i->"FAILED".equals(i.getStatus())).count());
        if("OPEN".equals(task.getStatus()) && result.getTotal()==result.getSuccess()+result.getSkipped()) result.setStatus("COMPLETED");
        result.setItems(BeanUtils.toBean(list,Item.class));return result;
    }
    public PageResult<Task> page(PageParam page,Long user) {
        var rows=tasks.selectPage(page,new LambdaQueryWrapperX<PmsKnowledgeImportTaskDO>()
                .eq(PmsKnowledgeImportTaskDO::getOwnerId,user).orderByDesc(PmsKnowledgeImportTaskDO::getId));
        var list=rows.getList().stream().map(t->{var r=get(t.getId(),user);r.setItems(null);return r;}).toList();
        return new PageResult<>(list,rows.getTotal());
    }
    public PageResult<Item> itemPage(Long id,PageParam page,Long user) {
        owned(id,user,false);
        var rows=items.selectPage(page,new LambdaQueryWrapperX<PmsKnowledgeImportItemDO>()
                .eq(PmsKnowledgeImportItemDO::getTaskId,id).orderByAsc(PmsKnowledgeImportItemDO::getId));
        return new PageResult<>(BeanUtils.toBean(rows.getList(),Item.class),rows.getTotal());
    }
    private boolean collision(Long lib,Long parent,String title) {
        return !matchingFolders(lib,parent,title).isEmpty() || documents.selectCount(new LambdaQueryWrapperX<PmsKnowledgeDocumentDO>()
                .eq(PmsKnowledgeDocumentDO::getLibraryId,lib).eq(PmsKnowledgeDocumentDO::getFolderId,parent)
                .eq(PmsKnowledgeDocumentDO::getParentId,0).eq(PmsKnowledgeDocumentDO::getTitle,title)
                .eq(PmsKnowledgeDocumentDO::getStatus,1))>0;
    }
    private List<PmsKnowledgeFolderDO> matchingFolders(Long lib,Long parent,String title) {
        return folders.selectList(new LambdaQueryWrapperX<PmsKnowledgeFolderDO>()
                .eq(PmsKnowledgeFolderDO::getLibraryId,lib).eq(PmsKnowledgeFolderDO::getParentId,parent)
                .eq(PmsKnowledgeFolderDO::getTitle,title).eq(PmsKnowledgeFolderDO::getStatus,1));
    }
    private Long resolve(Long lib,Long parent,String relative,boolean fresh,Long user,boolean create) {
        String[] parts=relative.split("/");
        for(int i=fresh?1:0;i<parts.length-1;i++) {
            var matches=matchingFolders(lib,parent,parts[i]);
            boolean documentExists=documents.selectCount(new LambdaQueryWrapperX<PmsKnowledgeDocumentDO>()
                    .eq(PmsKnowledgeDocumentDO::getLibraryId,lib).eq(PmsKnowledgeDocumentDO::getFolderId,parent)
                    .eq(PmsKnowledgeDocumentDO::getParentId,0).eq(PmsKnowledgeDocumentDO::getTitle,parts[i])
                    .eq(PmsKnowledgeDocumentDO::getStatus,1))>0;
            if(documentExists || matches.size()>1) throw exception(TARGET);
            if(matches.isEmpty()) {
                if(!create) return null;
                var req=new PmsKnowledgeFolderSaveReqVO();req.setLibraryId(lib);req.setParentId(parent);req.setTitle(parts[i]);
                parent=folderService.createFolder(req,user);
            } else { parent=matches.getFirst().getId();writable(lib,parent,user); }
        }
        return parent;
    }
    private void open(PmsKnowledgeImportTaskDO task,Long user) {
        if(!"OPEN".equals(task.getStatus())) throw exception(CLOSED);
        writable(task.getLibraryId(),task.getFolderId(),user);
    }
    private PmsKnowledgeImportItemDO item(Long taskId,Long itemId) {
        var i=items.selectById(itemId);
        if(i==null || !taskId.equals(i.getTaskId())) throw exception(DENIED);
        return i;
    }
    private void leased(PmsKnowledgeImportItemDO item,String token) {
        if(!"UPLOADING".equals(item.getStatus()) || !token.equals(item.getLeaseToken())) throw exception(BUSY);
    }
    public Task upload(Long taskId,Long itemId,MultipartFile file,Long user) {
        String token=UUID.randomUUID().toString();
        var claimed=tx.execute(s->{
            var task=owned(taskId,user,true);open(task,user);
            var i=item(taskId,itemId);
            if(Set.of("SUCCESS","SKIPPED").contains(i.getStatus())) return i;
            if("UPLOADING".equals(i.getStatus()) && i.getLeaseUntil()!=null && i.getLeaseUntil().isAfter(LocalDateTime.now())) throw exception(BUSY);
            if(file==null || file.getSize()!=i.getFileSize() || file.getSize()>policy.getMaxFileSize()) throw exception(INVALID,"文件大小已变化");
            i.setStatus("UPLOADING");i.setLeaseToken(token);i.setLeaseUntil(LocalDateTime.now().plusMinutes(15));
            i.setVersion(i.getVersion()+1);i.setReason("");items.updateById(i);return i;
        });
        if(Set.of("SUCCESS","SKIPPED").contains(claimed.getStatus())) return get(taskId,user);
        try {
            byte[] content=file.getBytes();
            if(!hash(content).equals(claimed.getDigest())) throw exception(INVALID,"文件已变化，请选择原文件");
            if(claimed.getFileId()==null) {
                String name=claimed.getRelativePath().substring(claimed.getRelativePath().lastIndexOf('/')+1);
                var stored=fileApi.createFileInfo(content,name,"pms/kb/import/"+taskId+"/"+itemId,null);
                tx.executeWithoutResult(s->{
                    owned(taskId,user,true);var i=item(taskId,itemId);leased(i,token);
                    i.setFileId(stored.getId());i.setFileUrl(stored.getUrl());items.updateById(i);
                });
            }
            tx.executeWithoutResult(s->{
                var task=owned(taskId,user,true);open(task,user);var i=item(taskId,itemId);leased(i,token);
                libraries.lockContentCreation(task.getLibraryId());
                Long folder=resolve(task.getLibraryId(),task.getFolderId(),i.getRelativePath(),"NEW".equals(task.getMode()),user,true);
                writable(task.getLibraryId(),folder,user);
                if(collision(task.getLibraryId(),folder,i.getTitle())) {i.setStatus("SKIPPED");i.setReason("目标冲突或不可写");}
                else {
                    var req=new PmsKnowledgeDocumentCreateReqVO();req.setLibraryId(task.getLibraryId());req.setFolderId(folder);
                    req.setParentId(0L);req.setTitle(i.getTitle());req.setType(4);req.setContent(i.getFileUrl());
                    req.setFileType(i.getFileType());req.setFileSize(i.getFileSize());
                    i.setDocumentId(documentService.createDocument(req,user));i.setFolderId(folder);i.setStatus("SUCCESS");i.setReason("");
                }
                items.updateById(i);
            });
        } catch(Exception e) {
            String reason=e instanceof ServiceException se?se.getMessage():"文件存储或登记失败，请重试";
            tx.executeWithoutResult(s->{
                owned(taskId,user,true);var i=item(taskId,itemId);
                if("UPLOADING".equals(i.getStatus()) && token.equals(i.getLeaseToken())) {i.setStatus("FAILED");i.setReason(reason);items.updateById(i);}
            });
            if(e instanceof ServiceException se) throw se;
            throw exception(STORAGE);
        }
        return get(taskId,user);
    }
    public Task resume(Resume req,Long user) {
        tx.executeWithoutResult(s->{
            var task=owned(req.getTaskId(),user,true);open(task,user);
            Map<String,Entry> files=new HashMap<>();
            for(var f:req.getFiles()) {path(f.getRelativePath(),task.getRootName(),policy.getMaxDepth());
                if(files.put(f.getRelativePath(),f)!=null) throw exception(INVALID,"重复路径");}
            for(var i:entries(task.getId())) {
                if(Set.of("SUCCESS","SKIPPED").contains(i.getStatus())) continue;
                if("UPLOADING".equals(i.getStatus()) && i.getLeaseUntil()!=null && i.getLeaseUntil().isAfter(LocalDateTime.now())) continue;
                var f=files.get(i.getRelativePath());
                String reason=f==null?"原文件未找到":(!Objects.equals(f.getFileSize(),i.getFileSize()) || !Objects.equals(f.getDigest(),i.getDigest())?"文件已变化":null);
                i.setStatus(reason==null?"PENDING":"FAILED");i.setReason(reason==null?"":reason);items.updateById(i);
            }
        });
        return get(req.getTaskId(),user);
    }
    public Task finish(Long id,Long user) {
        tx.executeWithoutResult(s->{var task=owned(id,user,true);
            if(entries(id).stream().anyMatch(i->"UPLOADING".equals(i.getStatus()) && i.getLeaseUntil()!=null && i.getLeaseUntil().isAfter(LocalDateTime.now()))) throw exception(BUSY);
            task.setStatus("FINISHED");tasks.updateById(task);
        });return get(id,user);
    }
}
