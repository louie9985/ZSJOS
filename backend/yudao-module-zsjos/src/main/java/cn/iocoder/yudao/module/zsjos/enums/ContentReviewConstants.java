package cn.iocoder.yudao.module.zsjos.enums;

import java.util.Set;

public interface ContentReviewConstants {

    String BPM_CATEGORY = "zsjos_content_review";
    String RELATION_DIRECTOR_OPERATOR = "content_director_operator";

    String BATCH_DRAFT = "DRAFT";
    String BATCH_DIRECTOR_REVIEW = "DIRECTOR_REVIEW";
    String BATCH_FINAL_REVIEW = "FINAL_REVIEW";
    String BATCH_COMPLETED = "COMPLETED";
    String BATCH_REJECTED = "REJECTED";
    /** A rejected batch remains available as a historical round while the operator prepares a new one. */
    String BATCH_NEED_MODIFY = "NEED_MODIFY";
    String BATCH_CANCELLED = "CANCELLED";

    String STAGE_DRAFT = "DRAFT";
    String STAGE_DIRECTOR = "DIRECTOR";
    String STAGE_FINAL = "FINAL";
    String STAGE_DONE = "DONE";

    String DECISION_APPROVED = "APPROVED";
    String DECISION_RETURNED = "RETURNED";
    Set<String> DECISIONS = Set.of(DECISION_APPROVED, DECISION_RETURNED);

    String RESULT_READY_TO_PUBLISH = "READY_TO_PUBLISH";
    String RESULT_PUBLISHED = "PUBLISHED";
    String RESULT_RETURNED = "RETURNED";

    String MATERIAL_TYPE_PRODUCTION_CONTENT = "production_content";

}
