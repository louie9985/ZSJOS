# UTF-8. Read-only compatibility query; execution never updates BPM or business tables.
# Run through the application endpoint under an authenticated tenant session:
#   GET /admin-api/zsjos/withdrawal/{id}/compatibility
# It classifies READY, RESULT_SYNC_REQUIRED, MANUAL_REVIEW_REQUIRED, INTEGRITY_ERROR, or NOT_PENDING.
# Only an explicitly approved scoped repair may call sync-process-result after review.
