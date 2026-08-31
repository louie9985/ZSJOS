package cn.iocoder.yudao.module.eam.service.publicedit;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.controller.pub.vo.EamPublicAssetUpdateReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.*;
import cn.iocoder.yudao.module.eam.dal.mysql.asset.*;
import cn.iocoder.yudao.module.eam.enums.asset.EamChangeTypeEnum;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetChangeLogService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.hrm.api.employee.HrmEmployeeApi;
import cn.iocoder.yudao.module.hrm.api.employee.dto.HrmEmployeeRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants;
import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class EamPublicEditService {
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    @Resource private EamPublicAssetTokenMapper tokenMapper;
    @Resource private EamAssetMapper assetMapper;
    @Resource private EamPublicEditCodeMapper codeMapper;
    @Resource private EamPublicEditAuditMapper auditMapper;
    @Resource private EamAssetService assetService;
    @Resource private EamAssetChangeLogService changeLogService;
    @Resource private HrmEmployeeApi employeeApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private EamCategoryService categoryService;
    @Resource private EamCategoryFieldService categoryFieldService;
    @Resource private DeptApi deptApi;
    @Resource private DictDataApi dictDataApi;
    @Value("${eam.public-asset.h5-base-url:}") private String h5BaseUrl;
    @Value("${eam.public-asset.passcode-encryption-key:}") private String encryptionKey;

    public record FieldOption(Object value, String label) {}
    public record PublicField(String key, String label, Object value, String type, boolean editable,
                              List<FieldOption> options) {}
    public record TreeOption(Object value, String label, List<TreeOption> children) {}
    public record EmployeeOption(Long value, String label, Long deptId) {}
    public record PublicAsset(Long version, List<PublicField> fields, Map<String,Object> editFields,
                              List<String> fileUrls, List<TreeOption> categoryTree, List<TreeOption> departmentTree,
                              List<EmployeeOption> employeeOptions) {}
    public record CodeResult(Long employeeId, String code) {}
    private record MatchedOperator(Long employeeId, Long userId) {}

    public PublicAsset getPublicAssetByCode(String assetCode) {
        var asset = resolvePublicAsset(assetCode);
        final PublicAsset[] result = new PublicAsset[1];
        TenantUtils.execute(asset.getTenantId(), () -> result[0] = buildPublicAsset(asset));
        return result[0];
    }

    private PublicAsset buildPublicAsset(EamAssetDO asset) {
        var categories = categoryService.getCategoryList();
        String categoryName = categories.stream().filter(item -> Objects.equals(item.getId(), asset.getCategoryId()))
                .map(item -> item.getName()).findFirst().orElse("-");
        List<HrmEmployeeRespDTO> employees = employeeApi.getEmployeeList();
        String employeeName = employees.stream().filter(item -> Objects.equals(item.getId(), asset.getUseEmployeeId()))
                .map(HrmEmployeeRespDTO::getName).findFirst().orElse(asset.getUseEmployeeNameSnapshot());
        Set<Long> deptIds = new LinkedHashSet<>();
        employees.stream().map(HrmEmployeeRespDTO::getDeptId).filter(Objects::nonNull).forEach(deptIds::add);
        if (asset.getUseDeptId() != null) deptIds.add(asset.getUseDeptId());
        List<DeptRespDTO> allDepts = deptApi.getChildDeptList(0L);
        if (allDepts.isEmpty() && !deptIds.isEmpty()) allDepts = new ArrayList<>(deptApi.getDeptList(deptIds));
        Map<Long, DeptRespDTO> deptMap = allDepts.stream().collect(Collectors.toMap(DeptRespDTO::getId, item -> item, (a, b) -> a));
        String deptName = Optional.ofNullable(deptMap.get(asset.getUseDeptId())).map(DeptRespDTO::getName).orElse("-");
        List<FieldOption> categoryOptions = categories.stream().map(item -> new FieldOption(item.getId(), item.getName())).toList();
        List<FieldOption> employeeOptions = employees.stream().map(item -> new FieldOption(item.getId(), item.getName())).toList();
        List<EmployeeOption> employeeDeptOptions = employees.stream()
                .map(item -> new EmployeeOption(item.getId(), item.getName(), item.getDeptId())).toList();
        List<FieldOption> deptOptions = deptMap.values().stream().map(item -> new FieldOption(item.getId(), item.getName())).toList();
        List<FieldOption> sourceOptions = dictDataApi.getDictDataList("eam_asset_source").stream()
                .map(item -> new FieldOption(Integer.valueOf(item.getValue()), item.getLabel())).toList();

        List<PublicField> fields = new ArrayList<>();
        add(fields, "assetCode", "资产编号", asset.getAssetCode(), "text", false, List.of());
        add(fields, "name", "资产名称", asset.getName(), "text", true, List.of());
        add(fields, "categoryId", "资产分类", categoryName, "select", true, categoryOptions);
        add(fields, "quantity", "数量", asset.getQuantity(), "number", true, List.of());
        add(fields, "brand", "品牌", asset.getBrand(), "text", true, List.of());
        add(fields, "specification", "规格型号", asset.getSpecification(), "text", true, List.of());
        add(fields, "sn", "序列号", asset.getSn(), "text", true, List.of());
        add(fields, "barcode", "条码", asset.getBarcode(), "text", true, List.of());
        add(fields, "originalValue", "资产原值", asset.getOriginalValue(), "number", true, List.of());
        add(fields, "netValue", "资产净值", asset.getNetValue(), "number", true, List.of());
        add(fields, "purchaseDate", "购入日期", asset.getPurchaseDate(), "date", true, List.of());
        add(fields, "source", "资产来源", asset.getSourceLabelSnapshot(), "select", true, sourceOptions);
        add(fields, "warrantyDate", "保修到期日", asset.getWarrantyDate(), "date", true, List.of());
        add(fields, "useDeptId", "使用部门", deptName, "select", true, deptOptions);
        add(fields, "useEmployeeId", "使用员工", employeeName, "select", true, employeeOptions);
        add(fields, "location", "存放地点", asset.getLocation(), "text", true, List.of());
        add(fields, "expectedLife", "预计使用年限（月）", asset.getExpectedLife(), "number", true, List.of());
        add(fields, "remark", "备注", asset.getRemark(), "textarea", true, List.of());
        Map<String, Object> extValues = asset.getExtFields() == null ? Map.of() : asset.getExtFields();
        Map<String, String> extLabels = asset.getExtFieldLabels() == null ? Map.of() : asset.getExtFieldLabels();
        for (EamCategoryFieldDO definition : categoryFieldService.getEffectiveFieldList(asset.getCategoryId())) {
            Object rawValue = extValues.get(definition.getFieldKey());
            Object displayValue = extLabels.getOrDefault(definition.getFieldKey(), rawValue == null ? "" : String.valueOf(rawValue));
            List<FieldOption> options = definition.getOptions() == null ? List.of()
                    : definition.getOptions().stream().map(value -> new FieldOption(value, value)).toList();
            add(fields, "ext." + definition.getFieldKey(), definition.getFieldName(), displayValue,
                    definition.getFieldType() != null && definition.getFieldType() == 5 ? "select" : "text", true, options);
        }
        Map<String,Object> editFields = new LinkedHashMap<>();
        editFields.put("version", asset.getVersion()); editFields.put("name", asset.getName()); editFields.put("categoryId", asset.getCategoryId());
        editFields.put("quantity", asset.getQuantity()); editFields.put("brand", asset.getBrand()); editFields.put("specification", asset.getSpecification());
        editFields.put("sn", asset.getSn()); editFields.put("barcode", asset.getBarcode()); editFields.put("originalValue", asset.getOriginalValue());
        editFields.put("netValue", asset.getNetValue()); editFields.put("purchaseDate", asset.getPurchaseDate()); editFields.put("source", asset.getSource());
        editFields.put("warrantyDate", asset.getWarrantyDate()); editFields.put("useDeptId", asset.getUseDeptId()); editFields.put("useEmployeeId", asset.getUseEmployeeId());
        editFields.put("location", asset.getLocation()); editFields.put("expectedLife", asset.getExpectedLife()); editFields.put("remark", asset.getRemark());
        editFields.put("extFields", new LinkedHashMap<>(extValues));
        return new PublicAsset(asset.getVersion() == null ? 0L : asset.getVersion(), fields, editFields, asset.getFileUrls(),
                buildCategoryTree(categories), buildDeptTree(allDepts), employeeDeptOptions);
    }

    private List<TreeOption> buildCategoryTree(List<EamCategoryDO> items) {
        Map<Long, List<EamCategoryDO>> children = items.stream().collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId(), LinkedHashMap::new, Collectors.toList()));
        return buildCategoryNodes(children, 0L);
    }
    private List<TreeOption> buildCategoryNodes(Map<Long, List<EamCategoryDO>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of()).stream().sorted(Comparator.comparing(EamCategoryDO::getSort, Comparator.nullsLast(Integer::compareTo)))
                .map(item -> new TreeOption(item.getId(), item.getName(), buildCategoryNodes(children, item.getId()))).toList();
    }
    private List<TreeOption> buildDeptTree(List<DeptRespDTO> items) {
        Map<Long, List<DeptRespDTO>> children = items.stream().collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId(), LinkedHashMap::new, Collectors.toList()));
        return buildDeptNodes(children, 0L);
    }
    private List<TreeOption> buildDeptNodes(Map<Long, List<DeptRespDTO>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of()).stream().sorted(Comparator.comparing(DeptRespDTO::getId))
                .map(item -> new TreeOption(item.getId(), item.getName(), buildDeptNodes(children, item.getId()))).toList();
    }

    private void add(List<PublicField> fields, String key, String label, Object value, String type,
                     boolean editable, List<FieldOption> options) {
        fields.add(new PublicField(key, label, value, type, editable, options));
    }

    public void verifyPublicEditCode(String assetCode, String passcode, String ip) {
        var asset = resolvePublicAsset(assetCode);
        TenantUtils.execute(asset.getTenantId(), () -> {
            if (passcode == null || !passcode.matches("[A-HJ-NP-Z2-9]{6}") || matchOperator(passcode) == null) {
                audit(asset.getId(), null, ip, "PASSCODE_INVALID", "口令不匹配");
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_NOT_EXISTS);
            }
        });
    }

    private EamAssetDO resolvePublicAsset(String assetCode) {
        if (assetCode == null || assetCode.isBlank()) throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_NOT_EXISTS);
        AtomicReference<List<EamAssetDO>> matches = new AtomicReference<>(List.of());
        TenantUtils.executeIgnore(() -> matches.set(assetMapper.selectListByAssetCode(assetCode.trim())));
        if (matches.get().size() != 1) throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_NOT_EXISTS);
        return matches.get().get(0);
    }

    @Transactional
    public String generatePublicToken(Long assetId) {
        assetService.validateAssetExists(assetId);
        String raw = RandomUtil.randomString(48);
        EamPublicAssetTokenDO token = tokenMapper.selectByAssetId(assetId);
        if (token == null) {
            token = new EamPublicAssetTokenDO();
            token.setAssetId(assetId);
            token.setVersion(1);
        } else {
            // Some deployed databases enforce one row per tenant/asset regardless of status.
            // Reusing the row also invalidates the previous raw token without a second insert.
            token.setVersion(token.getVersion() == null ? 1 : token.getVersion() + 1);
        }
        token.setTokenHash(sha256(raw));
        token.setStatus(1);
        token.setRevokedAt(null);
        if (token.getId() == null) tokenMapper.insert(token); else tokenMapper.updateById(token);
        return raw;
    }
    @Transactional public void revokePublicToken(Long assetId) { EamPublicAssetTokenDO t = tokenMapper.selectByAssetId(assetId); if (t != null) { t.setStatus(0); t.setRevokedAt(LocalDateTime.now()); tokenMapper.updateById(t); } }
    public String buildUrl(String assetCode) {
        return (h5BaseUrl == null ? "" : h5BaseUrl.replaceAll("/$", "")) + "/eam/asset?assetCode="
                + java.net.URLEncoder.encode(assetCode, StandardCharsets.UTF_8);
    }

    public CodeResult getOrCreateForCurrentUser(Long userId, boolean regenerate) {
        HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
        if (employee == null || !permissionApi.hasAnyPermissions(userId, "eam:asset:public-edit-code")) throw ServiceExceptionUtil.exception(ErrorCodeConstants.EMPLOYEE_NOT_EXISTS);
        EamPublicEditCodeDO existing = codeMapper.selectByEmployeeId(employee.getId());
        if (existing != null && !regenerate) return new CodeResult(employee.getId(), decrypt(existing.getEncryptedCode()));
        return saveCode(employee.getId(), userId, nextRandomCode());
    }
    @Transactional public CodeResult resetForUser(Long userId) { HrmEmployeeRespDTO e = employeeApi.getEmployeeByUserId(userId); if (e == null) throw ServiceExceptionUtil.exception(ErrorCodeConstants.EMPLOYEE_NOT_EXISTS); return saveCode(e.getId(), userId, nextRandomCode()); }
    @Transactional public CodeResult updateForCurrentUser(Long userId, String code) {
        HrmEmployeeRespDTO employee = employeeApi.getEmployeeByUserId(userId);
        if (employee == null || !permissionApi.hasAnyPermissions(userId, "eam:asset:public-edit-code")) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EMPLOYEE_NOT_EXISTS);
        }
        if (code == null || !code.matches("[A-HJ-NP-Z2-9]{6}")) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FIELD_VALUE_INVALID, "公开编辑口令");
        }
        EamPublicEditCodeDO duplicate = codeMapper.selectByCodeHmac(hmac(code));
        if (duplicate != null && !Objects.equals(duplicate.getEmployeeId(), employee.getId())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.FIELD_VALUE_INVALID, "公开编辑口令已被使用");
        }
        return saveCode(employee.getId(), userId, code);
    }
    private CodeResult saveCode(Long employeeId, Long userId, String code) { EamPublicEditCodeDO d = codeMapper.selectByEmployeeId(employeeId); if (d == null) { d = new EamPublicEditCodeDO(); d.setEmployeeId(employeeId); d.setUserId(userId); } d.setEncryptedCode(encrypt(code)); d.setCodeHmac(hmac(code)); d.setStatus(1); if (d.getId() == null) codeMapper.insert(d); else codeMapper.updateById(d); return new CodeResult(employeeId, code); }

    @Transactional
    public void updatePublicAssetByCode(String assetCode, EamPublicAssetUpdateReqVO publicReq, String passcode, String ip) {
        var asset = resolvePublicAsset(assetCode);
        TenantUtils.execute(asset.getTenantId(), () -> updatePublicAssetInTenant(asset, publicReq, passcode, ip));
    }

    private void updatePublicAssetInTenant(EamAssetDO asset, EamPublicAssetUpdateReqVO publicReq,
                                           String passcode, String ip) {
        if (passcode == null || !passcode.matches("[A-HJ-NP-Z2-9]{6}")) throw ServiceExceptionUtil.exception(ErrorCodeConstants.FIELD_VALUE_INVALID, "口令");
        MatchedOperator operator = matchOperator(passcode); if (operator == null) { audit(asset.getId(), null, ip, "PASSCODE_INVALID", "口令不匹配"); throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_NOT_EXISTS); }
        var before = assetService.validateAssetExists(asset.getId());
        EamAssetSaveReqVO req = BeanUtils.toBean(publicReq, EamAssetSaveReqVO.class);
        req.setId(asset.getId());
        int currentVersion = before.getVersion() == null ? 0 : before.getVersion();
        if (req.getVersion() == null || req.getVersion() != currentVersion) {
            audit(asset.getId(), operator.employeeId(), ip, "VERSION_CONFLICT", "资产已更新");
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_STATUS_INVALID, before.getAssetCode());
        }
        assetService.updateAsset(req, operator.userId());
        audit(asset.getId(), operator.employeeId(), ip, "SUCCESS", null);
    }

    @Transactional
    public void clearPublicAssetUsage(String assetCode, Integer version, String passcode, String ip) {
        var asset = resolvePublicAsset(assetCode);
        TenantUtils.execute(asset.getTenantId(), () -> {
            if (passcode == null || !passcode.matches("[A-HJ-NP-Z2-9]{6}")) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.FIELD_VALUE_INVALID, "口令");
            }
            MatchedOperator operator = matchOperator(passcode);
            if (operator == null) {
                audit(asset.getId(), null, ip, "PASSCODE_INVALID", "口令不匹配");
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.ASSET_NOT_EXISTS);
            }
            assetService.clearUsageAndSetIdle(asset.getId(), version, operator.userId());
            audit(asset.getId(), operator.employeeId(), ip, "CLEAR_USAGE_SUCCESS", null);
        });
    }

    private MatchedOperator matchOperator(String code) {
        byte[] submittedHmac = hmac(code).getBytes(StandardCharsets.UTF_8);
        Set<Long> enabledEditors = permissionApi.getEnabledUserIdsByPermission("eam:asset:public-edit-code");
        for (HrmEmployeeRespDTO employee : employeeApi.getEmployeeList()) {
            if (employee.getUserId() == null || !enabledEditors.contains(employee.getUserId())) {
                continue;
            }
            EamPublicEditCodeDO stored = codeMapper.selectByEmployeeId(employee.getId());
            if (stored != null && stored.getUserId() != null && Objects.equals(stored.getStatus(), 1)
                    && stored.getCodeHmac() != null
                    && MessageDigest.isEqual(submittedHmac, stored.getCodeHmac().getBytes(StandardCharsets.UTF_8))) {
                return new MatchedOperator(employee.getId(), stored.getUserId());
            }
        }
        return null;
    }
    private void audit(Long assetId, Long employeeId, String ip, String result, String reason) { EamPublicEditAuditDO a = new EamPublicEditAuditDO(); a.setAssetId(assetId); a.setEmployeeId(employeeId); a.setClientIp(ip); a.setResultCode(result); a.setFailureReason(reason); auditMapper.insert(a); }
    private String randomCode() { StringBuilder b = new StringBuilder(6); for (int i=0;i<6;i++) b.append(CHARSET.charAt(RandomUtil.randomInt(CHARSET.length()))); return b.toString(); }
    private String nextRandomCode() { for (int i = 0; i < 20; i++) { String code = randomCode(); if (codeMapper.selectByCodeHmac(hmac(code)) == null) return code; } throw ServiceExceptionUtil.exception(ErrorCodeConstants.FIELD_VALUE_INVALID, "公开编辑口令生成失败"); }
    private String sha256(String s) { return SecureUtil.sha256(s == null ? "" : s); }
    private String hmac(String s) { HMac mac = SecureUtil.hmac(HmacAlgorithm.HmacSHA256, "eam-public-edit-code".getBytes(StandardCharsets.UTF_8)); return mac.digestHex(s); }
    private String encrypt(String s) { if (encryptionKey == null || encryptionKey.isBlank()) return s; return SecureUtil.aes(Arrays.copyOf(encryptionKey.getBytes(StandardCharsets.UTF_8), 16)).encryptHex(s); }
    private String decrypt(String s) { if (encryptionKey == null || encryptionKey.isBlank()) return s; return SecureUtil.aes(Arrays.copyOf(encryptionKey.getBytes(StandardCharsets.UTF_8), 16)).decryptStr(s, StandardCharsets.UTF_8); }
}
