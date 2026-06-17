export type ApiResponse<T> = {
  isSuccess: boolean
  code: string
  message: string
  result: T
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

export type User = {
  userId: number
  email: string
  name: string
  role: 'USER' | 'ADMIN'
}

export type Concert = {
  concertId: number
  title: string
  venue: string
  posterUrl: string
}

export type ConcertDetail = Concert & {
  description: string
}

export type Schedule = {
  scheduleId: number
  performanceAt: string
  bookingOpenAt: string
  bookingCloseAt: string
  status: 'UPCOMING' | 'BOOKING_OPEN' | 'BOOKING_CLOSED' | 'CANCELED'
}

export type SeatLayout = {
  seatLayoutId: number
  name: string
  venueName: string
  description: string
  totalSeatCount: number
}

export type QueuePosition = {
  scheduleId: number
  position: number
  totalWaiting: number
  status: 'WAITING' | 'ENTERABLE'
}

export type EntryTokenResult = {
  scheduleId: number
  entryToken: string
  expiresAt: string
  alreadyIssued: boolean
}

export type SeatSection = {
  sectionId: number
  name: string
  grade: string
  price: number
}

export type Seat = {
  seatId: number
  sectionId: number
  rowName: string
  seatNumber: number
  status: 'AVAILABLE' | 'HELD' | 'RESERVED' | 'BLOCKED'
}

export type SeatHold = {
  holdId: string
  scheduleId: number
  seatIds: number[]
  expiresAt: string
}

export type ReservationSeat = {
  seatId: number
  sectionId: number
  sectionName: string
  rowName: string
  seatNumber: number
  price: number
}

export type Reservation = {
  reservationId: number
  scheduleId: number
  holdId: string
  paymentId: string | null
  status: 'PENDING_PAYMENT' | 'PAYMENT_PROCESSING' | 'CONFIRMED' | 'CANCELED' | 'EXPIRED'
  totalAmount: number
  expiresAt: string
  seats: ReservationSeat[]
}

export type PaymentRequest = {
  reservationId: number
  paymentId: string
  status: 'PAYMENT_PROCESSING'
}

export type PaymentPreparation = {
  paymentId: string
  status: 'PROCESSING' | 'READY' | 'SUCCESS' | 'FAILED'
}

export type Payment = {
  paymentId: string
  reservationId: number
  amount: number
  status: 'PENDING' | 'SUCCESS' | 'FAILED'
}
