import request from '@/config/axios'
export type PointsPackage = { id?: number; name: string; creditsAmount: number; validDays?: number | null; enabled: boolean }
export const list = (): Promise<PointsPackage[]> => request.get({ url: '/zsjos/points-redemption/package-list' })
export const save = (data: PointsPackage): Promise<number> => request.post({ url: '/zsjos/points-redemption/package-save', data })
