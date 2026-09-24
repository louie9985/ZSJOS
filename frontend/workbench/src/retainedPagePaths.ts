import { APP_ROUTES } from './constants'

// Runtime retention policy, not a source of menu authorization.
export const RETAINED_PAGE_PATHS: readonly string[] = [
  APP_ROUTES.CONTENT_REVIEW,
  APP_ROUTES.MEDIA_STUDENTS,
  APP_ROUTES.VIRAL_ACCOUNT_DECOMPOSE,
  APP_ROUTES.VIRAL_CONTENT_DECOMPOSE
]
