package cn.iocoder.yudao.module.zsjos.enums;

public interface ContentReviewNotifySceneConstants {

    String SCENE_PREFIX = "zsjos.content_review.";

    String BATCH_SUBMITTED = SCENE_PREFIX + "batch_submitted";
    String DIRECTOR_ITEM_DECISION = SCENE_PREFIX + "director_item_decision";
    String DIRECTOR_COMPLETED = SCENE_PREFIX + "director_completed";
    String DIRECTOR_REJECTED = SCENE_PREFIX + "director_rejected";
    String FINAL_ITEM_DECISION = SCENE_PREFIX + "final_item_decision";
    String FINAL_COMPLETED = SCENE_PREFIX + "final_completed";
    String FINAL_REJECTED = SCENE_PREFIX + "final_rejected";
    String REVIEW_TIMEOUT_REMINDER = SCENE_PREFIX + "review_timeout_reminder";
}
