import { createContext, useContext } from 'react'
import { contentReviewStudentApi } from '../services/contentReviewStudent'

/** Explicit read transport for existing student components; no command API is provided. */
export type MediaStudentReviewContextValue = { batchId: number; accountId: number }
export const MediaStudentReviewContext = createContext<MediaStudentReviewContextValue | undefined>(undefined)
export const useMediaStudentReview = () => useContext(MediaStudentReviewContext)
export const reviewFile = (scope: MediaStudentReviewContextValue, kind: 'profile' | 'interview' | 'positioning' | 'evidence', id: number, submissionId?: number) =>
  contentReviewStudentApi.file(scope.batchId, scope.accountId, kind, id, submissionId)
