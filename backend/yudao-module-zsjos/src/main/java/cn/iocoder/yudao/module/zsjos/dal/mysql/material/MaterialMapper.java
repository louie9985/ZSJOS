package cn.iocoder.yudao.module.zsjos.dal.mysql.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface MaterialMapper extends BaseMapperX<MaterialDO> {

    default PageResult<MaterialDO> selectPage(MaterialPageReqVO req, Long userId, boolean canManage) {
        LambdaQueryWrapperX<MaterialDO> query = new LambdaQueryWrapperX<MaterialDO>()
                .eqIfPresent(MaterialDO::getMaterialTypeId, req.getMaterialTypeId())
                .eqIfPresent(MaterialDO::getSource, req.getSource());
        if (canManage) {
            query.eqIfPresent(MaterialDO::getStatus, req.getStatus());
            if (Boolean.TRUE.equals(req.getMine())) query.eq(MaterialDO::getOwnerUserId, userId);
        } else if (Boolean.TRUE.equals(req.getMine())) {
            query.eq(MaterialDO::getOwnerUserId, userId).eqIfPresent(MaterialDO::getStatus, req.getStatus());
        } else {
            query.eq(MaterialDO::getStatus, "EFFECTIVE")
                    .isNotNull(MaterialDO::getCurrentEffectiveVersionId)
                    .apply("EXISTS (SELECT 1 FROM zsjos_material_type mt WHERE mt.id=zsjos_material.material_type_id "
                            + "AND mt.tenant_id=zsjos_material.tenant_id AND mt.status=0 AND mt.deleted=b'0')");
        }
        // A published material stays EFFECTIVE while its revision is under review.
        // Personal filters must follow the selected revision, not the public material status.
        if (Boolean.TRUE.equals(req.getMine()) && req.getVersionStatus() != null) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_material_version mv WHERE "
                    + "mv.id=COALESCE(zsjos_material.current_draft_version_id,zsjos_material.current_effective_version_id) "
                    + "AND mv.tenant_id=zsjos_material.tenant_id AND mv.deleted=b'0' AND mv.status={0})",
                    req.getVersionStatus());
        }
        if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
            query.and(item -> item.like(MaterialDO::getMaterialNo, req.getKeyword())
                    .or().like(MaterialDO::getTitle, req.getKeyword())
                    .or().like(MaterialDO::getSummary, req.getKeyword())
                    .or().apply("EXISTS (SELECT 1 FROM zsjos_material_version mv WHERE "
                            + "mv.id=zsjos_material.current_effective_version_id AND mv.tenant_id=zsjos_material.tenant_id "
                            + "AND mv.deleted=b'0' AND mv.search_text LIKE CONCAT('%',{0},'%'))", req.getKeyword()));
        }
        if (Boolean.TRUE.equals(req.getFavorite())) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_material_favorite f WHERE f.material_id=zsjos_material.id "
                    + "AND f.user_id={0} AND f.active=b'1' AND f.tenant_id=zsjos_material.tenant_id "
                    + "AND f.deleted=b'0')", userId);
        }
        appendDimension(query, "account_type", req.getAccountType(), req.getRecommendation());
        appendDimension(query, "profession", req.getProfession(), req.getRecommendation());
        appendDimension(query, "account_stage", req.getAccountStage(), req.getRecommendation());
        if(req.getPlatform()!=null && !req.getPlatform().isBlank()) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_material_version pv WHERE pv.id=zsjos_material.current_effective_version_id "
                    + "AND pv.tenant_id=zsjos_material.tenant_id AND pv.deleted=b'0' "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(pv.values_json,'$.account_platform'))={0})",req.getPlatform());
        }
        if (Boolean.TRUE.equals(req.getRecommendation())) {
            query.eq(MaterialDO::getStatus, "EFFECTIVE").isNotNull(MaterialDO::getCurrentEffectiveVersionId)
                    .apply("EXISTS (SELECT 1 FROM zsjos_material_type mt WHERE mt.id=zsjos_material.material_type_id "
                            + "AND mt.tenant_id=zsjos_material.tenant_id AND mt.status=0 "
                            + "AND mt.recommendation_enabled=b'1' AND mt.deleted=b'0')");
        }
        query.orderByDesc(MaterialDO::getPinned).orderByDesc(MaterialDO::getPriority)
                .orderByDesc(MaterialDO::getUpdateTime).orderByDesc(MaterialDO::getId);
        return selectPage(req, query);
    }

    default PageResult<MaterialDO> selectRecommendationPage(MaterialPageReqVO req, Long userId, Long tenantId,
                                                            String accountTypePrimary,
                                                            String accountTypeSecondary,
                                                            String professionPrimary,
                                                            String professionSecondary,
                                                            String accountStage,
                                                            String accountTypeTypeIds,
                                                            String professionTypeIds,
                                                            String accountStageTypeIds) {
        Page<MaterialDO> page = MyBatisUtils.buildPage(req);
        List<MaterialDO> rows = selectRecommendationRows(page, req, userId, tenantId,
                accountTypePrimary, accountTypeSecondary, professionPrimary, professionSecondary, accountStage,
                accountTypeTypeIds, professionTypeIds, accountStageTypeIds);
        return new PageResult<>(rows, page.getTotal());
    }

    @SelectProvider(type = RecommendationSqlProvider.class, method = "selectSql")
    List<MaterialDO> selectRecommendationRows(Page<?> page, @Param("request") MaterialPageReqVO request,
                                              @Param("userId") Long userId, @Param("tenantId") Long tenantId,
                                              @Param("accountTypePrimary") String accountTypePrimary,
                                              @Param("accountTypeSecondary") String accountTypeSecondary,
                                              @Param("professionPrimary") String professionPrimary,
                                              @Param("professionSecondary") String professionSecondary,
                                              @Param("accountStage") String accountStage,
                                              @Param("accountTypeTypeIds") String accountTypeTypeIds,
                                              @Param("professionTypeIds") String professionTypeIds,
                                              @Param("accountStageTypeIds") String accountStageTypeIds);

    final class RecommendationSqlProvider {
        private RecommendationSqlProvider() {
        }

        public static String selectSql() {
            return "<script>SELECT m.* FROM zsjos_material m "
                    + "JOIN zsjos_material_type mt ON mt.id=m.material_type_id AND mt.tenant_id=m.tenant_id "
                    + "AND mt.deleted=b'0' AND mt.status=0 AND mt.recommendation_enabled=b'1' "
                    + "JOIN zsjos_material_version mv ON mv.id=m.current_effective_version_id "
                    + "AND mv.tenant_id=m.tenant_id AND mv.deleted=b'0' AND mv.status='EFFECTIVE' "
                    + "WHERE m.tenant_id=#{tenantId} AND m.deleted=b'0' AND m.status='EFFECTIVE' "
                    + "<if test='request.materialTypeId != null'>AND m.material_type_id=#{request.materialTypeId} </if>"
                    + "<if test='request.source != null and request.source != \"\"'>AND m.source=#{request.source} </if>"
                    + "<if test='request.keyword != null and request.keyword != \"\"'>"
                    + "AND (m.material_no LIKE CONCAT('%',#{request.keyword},'%') "
                    + "OR m.title LIKE CONCAT('%',#{request.keyword},'%') "
                    + "OR m.summary LIKE CONCAT('%',#{request.keyword},'%') "
                    + "OR mv.search_text LIKE CONCAT('%',#{request.keyword},'%')) </if>"
                    + "<if test='request.favorite == true'>AND EXISTS (SELECT 1 FROM zsjos_material_favorite f "
                    + "WHERE f.material_id=m.id AND f.user_id=#{userId} AND f.active=b'1' "
                    + "AND f.tenant_id=m.tenant_id AND f.deleted=b'0') </if>"
                    + dimensionFilter("account_type", "accountTypePrimary", "accountTypeSecondary", "accountTypeTypeIds")
                    + dimensionFilter("profession", "professionPrimary", "professionSecondary", "professionTypeIds")
                    + dimensionFilter("account_stage", "accountStage", null, "accountStageTypeIds")
                    + "ORDER BY " + dimensionScore() + " DESC, " + primaryScore() + " DESC, "
                    + secondaryScore() + " DESC, m.pinned DESC, m.priority DESC, mv.effective_at DESC, m.id DESC"
                    + "</script>";
        }

        private static String dimensionFilter(String dimension, String primary, String secondary, String typeIds) {
            String hasValue = primary + " != null and " + primary + " != \"\"";
            if (secondary != null) hasValue += " or " + secondary + " != null and " + secondary + " != \"\"";
            String exact = "d.dimension_value=#{" + primary + "}";
            if (secondary != null) exact += " OR d.dimension_value=#{" + secondary + "}";
            return "AND (FIND_IN_SET(mt.id,#{" + typeIds + "})=0 OR EXISTS (SELECT 1 "
                    + "FROM zsjos_material_dimension d WHERE d.material_version_id=mv.id "
                    + "AND d.tenant_id=m.tenant_id AND d.deleted=b'0' AND d.dimension_key='" + dimension + "' "
                    + "<choose><when test='" + hasValue + "'>AND (d.unlimited=b'1' OR " + exact + ") "
                    + "</when><otherwise>AND d.unlimited=b'1' </otherwise></choose>)) ";
        }

        private static String dimensionScore() {
            return "((CASE WHEN " + exactExists("account_type", "accountTypePrimary", "accountTypeSecondary",
                    "accountTypeTypeIds")
                    + " THEN 1 ELSE 0 END)+(CASE WHEN "
                    + exactExists("profession", "professionPrimary", "professionSecondary", "professionTypeIds")
                    + " THEN 1 ELSE 0 END)+(CASE WHEN "
                    + exactExists("account_stage", "accountStage", null, "accountStageTypeIds") + " THEN 1 ELSE 0 END))";
        }

        private static String primaryScore() {
            return "((CASE WHEN " + exactExists("account_type", "accountTypePrimary", null, "accountTypeTypeIds")
                    + " THEN 1 ELSE 0 END)+(CASE WHEN "
                    + exactExists("profession", "professionPrimary", null, "professionTypeIds")
                    + " THEN 1 ELSE 0 END)+(CASE WHEN "
                    + exactExists("account_stage", "accountStage", null, "accountStageTypeIds") + " THEN 1 ELSE 0 END))";
        }

        private static String secondaryScore() {
            return "((CASE WHEN " + exactExists("account_type", "accountTypeSecondary", null, "accountTypeTypeIds")
                    + " THEN 1 ELSE 0 END)+(CASE WHEN "
                    + exactExists("profession", "professionSecondary", null, "professionTypeIds") + " THEN 1 ELSE 0 END))";
        }

        private static String exactExists(String dimension, String first, String second, String typeIds) {
            String values = "d.dimension_value=#{" + first + "}";
            if (second != null) values += " OR d.dimension_value=#{" + second + "}";
            return "FIND_IN_SET(mt.id,#{" + typeIds + "})>0 AND EXISTS (SELECT 1 FROM zsjos_material_dimension d "
                    + "WHERE d.material_version_id=mv.id "
                    + "AND d.tenant_id=m.tenant_id AND d.deleted=b'0' AND d.unlimited=b'0' "
                    + "AND d.dimension_key='" + dimension + "' AND (" + values + "))";
        }
    }

    private static void appendDimension(LambdaQueryWrapperX<MaterialDO> query, String key, String value,
                                        Boolean recommendation) {
        if (!Boolean.TRUE.equals(recommendation) && (value == null || value.isBlank())) {
            return;
        }
        if (value == null || value.isBlank()) {
            query.apply("EXISTS (SELECT 1 FROM zsjos_material_dimension d WHERE "
                    + "d.material_version_id=zsjos_material.current_effective_version_id "
                    + "AND d.dimension_key={0} AND d.unlimited=b'1' AND d.deleted=b'0')", key);
        } else {
            query.apply("EXISTS (SELECT 1 FROM zsjos_material_dimension d WHERE "
                    + "d.material_version_id=zsjos_material.current_effective_version_id "
                    + "AND d.dimension_key={0} AND (d.dimension_value={1} OR d.unlimited=b'1') AND d.deleted=b'0')", key, value);
        }
    }

    @Select("SELECT * FROM zsjos_material WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted=b'0' FOR UPDATE")
    MaterialDO selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    default int updateDraftPointer(Long id, Integer expectedVersion, Long draftVersionId, String status) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>()
                .eq(MaterialDO::getId, id).eq(MaterialDO::getVersion, expectedVersion)
                .set(MaterialDO::getCurrentDraftVersionId, draftVersionId)
                .set(MaterialDO::getStatus, status)
                .set(MaterialDO::getVersion, expectedVersion + 1));
    }

    default int updateDraftSummary(MaterialDO material, Long draftVersionId, String status,
                                   String title, String coverSnapshotJson, String summary) {
        LambdaUpdateWrapper<MaterialDO> update = new LambdaUpdateWrapper<MaterialDO>()
                .eq(MaterialDO::getId, material.getId()).eq(MaterialDO::getVersion, material.getVersion())
                .set(MaterialDO::getCurrentDraftVersionId, draftVersionId)
                .set(MaterialDO::getStatus, status)
                .set(MaterialDO::getVersion, material.getVersion() + 1);
        if (material.getCurrentEffectiveVersionId() == null) {
            update.set(MaterialDO::getTitle, title)
                    .set(MaterialDO::getCoverSnapshotJson, coverSnapshotJson)
                    .set(MaterialDO::getSummary, summary);
        }
        return update(null, update);
    }

    default int finishRejectedVersion(MaterialDO material, String targetStatus) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>()
                .eq(MaterialDO::getId, material.getId()).eq(MaterialDO::getVersion, material.getVersion())
                .set(MaterialDO::getStatus, targetStatus)
                .set(MaterialDO::getVersion, material.getVersion() + 1));
    }

    default int updateDisabled(MaterialDO material, String status, String reason,
                               java.time.LocalDateTime disabledAt, Long disabledByUserId) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>()
                .eq(MaterialDO::getId, material.getId()).eq(MaterialDO::getVersion, material.getVersion())
                .set(MaterialDO::getStatus, status)
                .set(MaterialDO::getDisabledReason, reason)
                .set(MaterialDO::getDisabledAt, disabledAt)
                .set(MaterialDO::getDisabledByUserId, disabledByUserId)
                .set(MaterialDO::getVersion, material.getVersion() + 1));
    }

    default int activateVersion(MaterialDO material, Long versionId, String title, String coverSnapshotJson,
                                String summary) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>()
                .eq(MaterialDO::getId, material.getId()).eq(MaterialDO::getVersion, material.getVersion())
                .set(MaterialDO::getCurrentEffectiveVersionId, versionId)
                .set(MaterialDO::getCurrentDraftVersionId, null)
                .set(MaterialDO::getStatus, "EFFECTIVE")
                .set(MaterialDO::getTitle, title).set(MaterialDO::getCoverSnapshotJson, coverSnapshotJson)
                .set(MaterialDO::getSummary, summary)
                .set(MaterialDO::getVersion, material.getVersion() + 1));
    }

    default int incrementLike(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>().eq(MaterialDO::getId, id)
                .setSql("like_count = GREATEST(0, like_count + " + delta + ")"));
    }

    default int incrementFavorite(Long id, int delta) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>().eq(MaterialDO::getId, id)
                .setSql("favorite_count = GREATEST(0, favorite_count + " + delta + ")"));
    }

    default int incrementReference(Long id) {
        return update(null, new LambdaUpdateWrapper<MaterialDO>().eq(MaterialDO::getId, id)
                .setSql("reference_count = reference_count + 1"));
    }
}
