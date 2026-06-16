export const COMPETITION_GENERATOR_URL =
  import.meta.env.VITE_VIRTUAL_USER_GENERATOR_URL ?? 'http://localhost:8085'

export type CompetitionStatus =
  | 'IDLE'
  | 'PREPARING'
  | 'READY'
  | 'WAITING'
  | 'COMPLETED'
  | 'FAILED'

export type CompetitionEvent = {
  userNumber: number
  status: string
  detail: string
  occurredAt: string
}

export type CompetitionSnapshot = {
  runId: string | null
  status: CompetitionStatus
  gatewayBaseUrl: string
  scheduleId: number | null
  totalUsers: number
  completedUsers: number
  startAt: string | null
  updatedAt: string
  userStatuses: Record<string, number>
  recentEvents: CompetitionEvent[]
}

export type CompetitionStartRequest = {
  scheduleId: number
  virtualUsers: number
  startAt: string
  prepareConcurrency: number
  joinJitterMillis: number
  behaviors: {
    abandonQueue: number
    abandonAfterEntry: number
    abandonAfterHold: number
    paymentFailure: number
    paymentSuccess: number
  }
}

async function generatorRequest<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(`${COMPETITION_GENERATOR_URL}${path}`, {
    ...options,
    headers: options.body
      ? { 'Content-Type': 'application/json', ...options.headers }
      : options.headers,
  })
  const body = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(body?.message ?? '가상 사용자 생성기 요청에 실패했습니다.')
  }
  return body as T
}

export const competitionApi = {
  status: () =>
    generatorRequest<CompetitionSnapshot>('/api/competitions'),

  accountPool: () =>
    generatorRequest<{ storedAccounts: number }>('/api/virtual-user-accounts'),

  start: (request: CompetitionStartRequest) =>
    generatorRequest<CompetitionSnapshot>('/api/competitions', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  stop: () =>
    generatorRequest<void>('/api/competitions', { method: 'DELETE' }),

  eventUrl: `${COMPETITION_GENERATOR_URL}/api/competitions/events`,
}
