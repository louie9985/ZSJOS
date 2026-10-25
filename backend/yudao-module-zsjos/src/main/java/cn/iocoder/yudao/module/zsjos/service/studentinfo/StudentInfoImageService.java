package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentInfoImageService {
    @Resource private StudentInfoImageMapper images;
    @Resource private StudentInfoFormMapper forms;
    @Resource private FileApi files;
    @Resource @Lazy private StudentInfoFormService service;
    @Value("${mybatis-plus.encryptor.password:}") private String key;

    public List<Image> list(Long formId) { return images.byForm(formId).stream().map(this::project).toList(); }
    public List<Image> archiveList(Long formId, List<Long> ids) {
        return ids.stream().map(id -> project(requireImageId(formId,id))).toList();
    }
    private StudentInfoImageDO requireImageId(Long formId,Long id) {
        var row=images.selectById(id);
        if (row==null || !Objects.equals(row.getFormId(),formId)) throw exception(LINK_INVALID);
        return row;
    }
    public byte[] archiveRead(Long formId,Long id) { return read(requireImageId(formId,id)); }
    public Image archiveUpload(StudentInfoFormDO form,String fieldKey,String side,MultipartFile upload) {
        var field=imageField(form,fieldKey);
        if ("identity_images".equals(fieldKey) ? !Set.of("front","back").contains(side) : side!=null && !side.isEmpty()) throw exception(FIELD_INVALID,field.getLabel());
        validateAttachmentSize(upload,field);
        try {
            byte[] content=upload.getBytes(); String type=validateAttachment(content,upload.getOriginalFilename(),field);
            var stored=files.createFileInfo(crypt(content,true),UUID.randomUUID()+".bin","student-info-private/"+form.getId(),"application/octet-stream");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status!=STATUS_COMMITTED) try { files.deleteFileIfExists(stored.getId()); } catch(Exception ignored) { } }
            });
            var row=new StudentInfoImageDO(); row.setFormId(form.getId()); row.setFieldKey(fieldKey); row.setSide(side==null ? "" : side);
            row.setInfraFileId(stored.getId()); row.setFileSize((long)content.length); row.setContentType(type); row.setArchiveOnly(true);
            row.setTenantId(form.getTenantId()); images.insert(row); return project(row);
        } catch(cn.iocoder.yudao.framework.common.exception.ServiceException error) { throw error; }
        catch(Exception error) { throw exception(FIELD_INVALID,"图片上传失败，请重试"); }
    }
    public void validateArchive(StudentInfoFormDO form,List<Field> fields,List<Long> ids) {
        var rows=ids.stream().map(id->requireImageId(form.getId(),id)).toList();
        if (new HashSet<>(ids).size()!=ids.size()) throw exception(FIELD_INVALID,"重复图片");
        for (var field:fields) if ("images".equals(field.getType())) {
            var selected=rows.stream().filter(i->field.getKey().equals(i.getFieldKey())).toList();
            boolean identity="identity_images".equals(field.getKey());
            if (selected.size()>(identity ? 2 : field.getMaxImages()) || field.getRequired() && selected.size()<(identity ? 2 : 1)) throw exception(FIELD_INVALID,field.getLabel());
            if (identity && (selected.stream().anyMatch(i->!Set.of("front","back").contains(i.getSide()))
                    || selected.stream().map(StudentInfoImageDO::getSide).distinct().count()!=selected.size())) throw exception(FIELD_INVALID,field.getLabel());
        }
    }
    private Image project(StudentInfoImageDO row) {
        var result=new Image(); result.setId(row.getId()); result.setFieldKey(row.getFieldKey()); result.setSide(row.getSide());
        result.setContentType(row.getContentType()); result.setSize(row.getFileSize());
        result.setName(row.getFieldKey()+"-"+row.getId()+StudentInfoAttachmentValidator.extension(row.getContentType())); return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public Image upload(String token,String fieldKey,String side,MultipartFile upload) {
        return service.withLockedToken(token,form->{
            requireDraft(form); var field=imageField(form,fieldKey);
            boolean identity="identity_images".equals(fieldKey);
            if (identity ? !Set.of("front","back").contains(side) : side!=null && !side.isEmpty()) throw exception(FIELD_INVALID,field.getLabel());
            var existing=images.byForm(form.getId()).stream().filter(i->fieldKey.equals(i.getFieldKey())).toList();
            if (identity ? existing.stream().anyMatch(i->side.equals(i.getSide())) : existing.size()>=field.getMaxImages()) throw exception(FIELD_INVALID,"图片数量");
            validateAttachmentSize(upload,field);
            try {
                byte[] content=upload.getBytes(); String type=validateAttachment(content,upload.getOriginalFilename(),field);
                byte[] encrypted=crypt(content,true);
                var stored=files.createFileInfo(encrypted,UUID.randomUUID()+".bin","student-info-private/"+form.getId(),"application/octet-stream");
                // Infra storage is outside the database transaction; compensate only a failed binding transaction.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { if (status!=STATUS_COMMITTED) try { files.deleteFileIfExists(stored.getId()); } catch(Exception ignored) { /* retained encrypted orphan; never exposed as an image */ } }
                });
                var row=new StudentInfoImageDO(); row.setFormId(form.getId()); row.setFieldKey(fieldKey); row.setSide(identity ? side : "");
                row.setInfraFileId(stored.getId()); row.setFileSize((long)content.length); row.setContentType(type); images.insert(row); return project(row);
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException error) { throw error; }
            catch (Exception error) { throw exception(FIELD_INVALID,"图片上传失败，请重试"); }
        });
    }
    static String validateImage(byte[] content,String name) throws Exception {
        String ext=name==null ? "" : name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String expected=Set.of("jpg","jpeg").contains(ext) ? "jpeg" : "png".equals(ext) ? "png" : "";
        if (expected.isEmpty()) throw exception(FIELD_INVALID,"仅支持 JPG、PNG 图片");
        try (var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            var readers=ImageIO.getImageReaders(stream); if (!readers.hasNext()) throw exception(FIELD_INVALID,"图片内容");
            var reader=readers.next();
            try {
                reader.setInput(stream);
                if (!reader.getFormatName().equalsIgnoreCase(expected) || (long)reader.getWidth(0)*reader.getHeight(0)>40_000_000L || reader.read(0)==null) throw exception(FIELD_INVALID,"图片内容或尺寸");
            } finally { reader.dispose(); }
        }
        return "image/"+expected;
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(String token,Long id) {
        service.withLockedToken(token,form->{
            requireDraft(form); var image=requireImage(form,id); images.deleteById(image.getId());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { try { files.deleteFileIfExists(image.getInfraFileId()); } catch(Exception ignored) { /* encrypted orphan retained for scoped cleanup */ } }
            }); return null;
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public byte[] publicRead(String token,Long id) {
        return service.withLockedToken(token,form->{ requireDraft(form); return read(requireImage(form,id)); });
    }
    @ZsjosPermission(bizType="student-info",bizId="#caseId",action="read")
    public byte[] internalRead(Long caseId,Long id) {
        var form=forms.byCase(caseId); if (form==null || !"SUBMITTED".equals(form.getStatus())) throw exception(LINK_INVALID);
        return read(requireImage(form,id));
    }
    private byte[] read(StudentInfoImageDO image) {
        try { return crypt(files.getFileContent(image.getInfraFileId()),false); }
        catch (Exception error) { throw exception(FIELD_INVALID,"图片暂不可用"); }
    }
    @Transactional(rollbackFor=Exception.class)
    public int cleanupExpired() {
        int count=0;
        for (Long id:images.expiredFormIds()) {
            var form=forms.lock(id);
            if (form==null || "SUBMITTED".equals(form.getStatus()) || form.getExpiresAt()==null || !form.getExpiresAt().isBefore(java.time.LocalDateTime.now().minusDays(7))) continue;
            for (var image:images.byForm(id)) {
                try {
                    // File IDs originate only from this service; validate namespace again before physical cleanup.
                    var metadata=files.getFileInfo(image.getInfraFileId());
                    if (metadata!=null && (metadata.getPath()==null || !metadata.getPath().startsWith("student-info-private/"+id+"/"))) continue;
                    files.deleteFileIfExists(image.getInfraFileId()); images.deleteById(image.getId()); count++;
                } catch(Exception ignored) { /* retain the binding so the next scoped job can retry */ }
            }
        }
        return count;
    }
    byte[] crypt(byte[] data,boolean encrypt) throws Exception {
        if (key==null || key.isBlank()) throw exception(CONFIG_INVALID);
        byte[] derived=MessageDigest.getInstance("SHA-256").digest(("student-info-images:"+key).getBytes(StandardCharsets.UTF_8));
        byte[] nonce=new byte[12];
        if (encrypt) new SecureRandom().nextBytes(nonce);
        else { if (data.length<28) throw exception(FIELD_INVALID,"图片内容"); System.arraycopy(data,0,nonce,0,12); }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,new SecretKeySpec(derived,"AES"),new GCMParameterSpec(128,nonce));
        return encrypt ? ByteBuffer.allocate(12+data.length+16).put(nonce).put(cipher.doFinal(data)).array() : cipher.doFinal(data,12,data.length-12);
    }
    private void validateAttachmentSize(MultipartFile upload, Field field) {
        if (upload.isEmpty()) throw exception(ATTACHMENT_EMPTY);
        if (upload.getSize()>field.getMaxSizeMb()*1024L*1024) throw exception(ATTACHMENT_TOO_LARGE,field.getMaxSizeMb());
    }
    private String validateAttachment(byte[] content, String name, Field field) throws Exception {
        return StudentInfoAttachmentValidator.validate(content, name,
                field.getAllowedExtensions() == null ? List.of("jpg", "jpeg", "png") : field.getAllowedExtensions());
    }
    private void requireDraft(StudentInfoFormDO form) { if (!"DRAFT".equals(service.effectiveState(form))) throw exception(LINK_EXPIRED); }
    private Field imageField(StudentInfoFormDO form,String fieldKey) { return service.definitions(form).stream().filter(f->"images".equals(f.getType()) && f.getKey().equals(fieldKey)).findFirst().orElseThrow(()->exception(FIELD_INVALID,"附件字段")); }
    private StudentInfoImageDO requireImage(StudentInfoFormDO form,Long id) { var row=images.selectById(id); if (row==null || !Objects.equals(row.getFormId(),form.getId())) throw exception(LINK_INVALID); return row; }
    public void validateSubmission(StudentInfoFormDO form,List<Field> definitions,Map<String,Object> answers) {
        var rows=images.byForm(form.getId()); Set<Long> referenced=new HashSet<>();
        for (Field field:definitions) if ("images".equals(field.getType())) {
            Object raw=answers.get(field.getKey()); if (raw==null) raw=List.of();
            if (!(raw instanceof List<?> ids)) throw exception(FIELD_INVALID,field.getLabel());
            boolean identity="identity_images".equals(field.getKey());
            if (ids.size()>(identity ? 2 : field.getMaxImages()) || field.getRequired() && ids.size()<(identity ? 2 : 1)) throw exception(FIELD_INVALID,field.getLabel());
            Set<String> sides=new HashSet<>();
            for (Object id:ids) {
                if (!(id instanceof Number n) || !id.toString().matches("[0-9]+")) throw exception(FIELD_INVALID,field.getLabel());
                var row=rows.stream().filter(i->i.getId().equals(n.longValue()) && i.getFieldKey().equals(field.getKey())).findFirst().orElseThrow(()->exception(FIELD_INVALID,field.getLabel()));
                if (!referenced.add(row.getId()) || identity && !sides.add(row.getSide())) throw exception(FIELD_INVALID,field.getLabel());
            }
        }
        // All successfully uploaded images must be explicitly included or removed before the immutable submission.
        if (referenced.size()!=rows.size()) throw exception(FIELD_INVALID,"请刷新附件后重新提交");
    }
}
