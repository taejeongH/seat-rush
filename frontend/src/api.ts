import type {
  ApiResponse,
  Concert,
  ConcertDetail,
  EntryTokenResult,
  PageResponse,
  Payment,
  PaymentPreparation,
  PaymentRequest,
  QueuePosition,
  Reservation,
  Schedule,
  Seat,
  SeatHold,
  SeatSection,
  User,
} from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
const ACCESS_TOKEN_KEY = 'seat-rush-access-token'

export class ApiError extends Error {
  code: string
  status: number

  constructor(message: string, code = 'UNKNOWN', status = 500) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export const tokenStorage = {
  get: () => sessionStorage.getItem(ACCESS_TOKEN_KEY),
  set: (token: string) => sessionStorage.setItem(ACCESS_TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(ACCESS_TOKEN_KEY),
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  entryToken?: string,
  authenticated = true,
): Promise<T> {
  const headers = new Headers(options.headers)
  const accessToken = tokenStorage.get()

  if (authenticated && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }
  if (entryToken) headers.set('X-Entry-Token', entryToken)
  if (options.body) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  const body = (await response.json().catch(() => null)) as ApiResponse<T> | null

  if (!response.ok || !body?.isSuccess) {
    if (response.status === 401) tokenStorage.clear()
    throw new ApiError(
      body?.message ?? '요청을 처리하지 못했습니다.',
      body?.code,
      response.status,
    )
  }

  return body.result
}

const postJson = (body: unknown): RequestInit => ({
  method: 'POST',
  body: JSON.stringify(body),
})

export const api = {
  signup: (body: { email: string; password: string; name: string }) =>
    request<User>('/api/auth/signup', postJson(body), undefined, false),

  login: (body: { email: string; password: string }) =>
    request<{ accessToken: string; tokenType: string; expiresIn: number }>(
      '/api/auth/login',
      postJson(body),
      undefined,
      false,
    ),

  me: () => request<User>('/api/users/me'),

  concerts: () =>
    request<PageResponse<Concert>>(
      '/api/concerts?page=0&size=30',
      {},
      undefined,
      false,
    ),

  concert: (concertId: number) =>
    request<ConcertDetail>(
      `/api/concerts/${concertId}`,
      {},
      undefined,
      false,
    ),

  schedules: (concertId: number) =>
    request<Schedule[]>(
      `/api/concerts/${concertId}/schedules`,
      {},
      undefined,
      false,
    ),

  joinQueue: (scheduleId: number) =>
    request<QueuePosition & { alreadyJoined: boolean }>(
      `/api/schedules/${scheduleId}/queues/join`,
      { method: 'POST' },
    ),

  queuePosition: (scheduleId: number) =>
    request<QueuePosition>(`/api/schedules/${scheduleId}/queues/me`),

  enterQueue: (scheduleId: number) =>
    request<EntryTokenResult>(
      `/api/schedules/${scheduleId}/queues/enter`,
      { method: 'POST' },
    ),

  sections: (scheduleId: number, entryToken: string) =>
    request<SeatSection[]>(
      `/api/schedules/${scheduleId}/sections`,
      {},
      entryToken,
    ),

  seats: (scheduleId: number, sectionId: number, entryToken: string) =>
    request<Seat[]>(
      `/api/schedules/${scheduleId}/seats?sectionId=${sectionId}`,
      {},
      entryToken,
    ),

  holdSeats: (scheduleId: number, seatIds: number[], entryToken: string) =>
    request<SeatHold>(
      `/api/schedules/${scheduleId}/seats/hold`,
      postJson({ seatIds }),
      entryToken,
    ),

  createReservation: (holdId: string, entryToken: string) =>
    request<Reservation>(
      '/api/reservations',
      postJson({ holdId }),
      entryToken,
    ),

  reservation: (reservationId: number) =>
    request<Reservation>(`/api/reservations/${reservationId}`),

  requestPayment: (reservationId: number) =>
    request<PaymentRequest>(
      `/api/reservations/${reservationId}/payments`,
      { method: 'POST' },
    ),

  paymentPreparation: (paymentId: string) =>
    request<PaymentPreparation>(`/api/payments/${paymentId}`),

  completePayment: (paymentId: string, result: 'SUCCESS' | 'FAILED') =>
    request<Payment>(
      `/api/payments/${paymentId}/complete`,
      postJson({ result }),
    ),
}
