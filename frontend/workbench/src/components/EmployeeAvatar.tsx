import { Avatar, type AvatarProps } from 'antd'
import { createContext, useContext, useEffect, useMemo, useState } from 'react'

const DefaultEmployeeAvatarContext = createContext<string | undefined>(undefined)

export function DefaultEmployeeAvatarProvider({ defaultAvatar, children }: { defaultAvatar?: string; children: React.ReactNode }) {
  return <DefaultEmployeeAvatarContext.Provider value={defaultAvatar || undefined}>{children}</DefaultEmployeeAvatarContext.Provider>
}

export function getEmployeeAvatarCandidates(avatar?: string, defaultAvatar?: string) {
  return [avatar, defaultAvatar].map(value => value?.trim()).filter((value, index, values): value is string => Boolean(value) && values.indexOf(value) === index)
}

export function getNextEmployeeAvatarCandidate(candidates: string[], candidateIndex: number) {
  return candidates[candidateIndex + 1]
}

export default function EmployeeAvatar({ avatar, name, ...props }: Omit<AvatarProps, 'src'> & { avatar?: string; name?: string }) {
  const defaultAvatar = useContext(DefaultEmployeeAvatarContext)
  const candidates = useMemo(() => getEmployeeAvatarCandidates(avatar, defaultAvatar), [avatar, defaultAvatar])
  const [candidateIndex, setCandidateIndex] = useState(0)

  useEffect(() => setCandidateIndex(0), [candidates])

  const src = candidates[candidateIndex]
  return <Avatar
    {...props}
    src={src}
    onError={() => {
      setCandidateIndex(index => getNextEmployeeAvatarCandidate(candidates, index) ? index + 1 : candidates.length)
      return false
    }}
  >
    {name?.trim().slice(0, 1) || '员'}
  </Avatar>
}
