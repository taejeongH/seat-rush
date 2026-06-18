import { api } from './api'
import type {
  EntryTokenResult,
  Payment,
  PaymentPreparation,
  PaymentRequest,
  QueuePosition,
  Reservation,
  Seat,
  SeatHold,
  SeatSection,
} from './types'

/**
 * 예매 진행 시 실제 모드와 연습 모드(경쟁 모드)의 API 호출 분기를 일관되게 처리하기 위한
 * 클라이언트 래퍼 서비스를 생성합니다.
 *
 * @param practiceSessionId 연습 세션 ID (실제 모드인 경우 null)
 */
export function getBookingApi(practiceSessionId: string | null) {
  return {
    /**
     * 대기열 진입 요청을 보냅니다.
     */
    joinQueue: (scheduleId: number): Promise<QueuePosition & { alreadyJoined: boolean }> =>
      practiceSessionId
        ? api.joinPracticeQueue(practiceSessionId, scheduleId)
        : api.joinQueue(scheduleId),

    /**
     * 대기열 내 현재 순번 및 입장 가용 상태를 조회합니다.
     */
    queuePosition: (scheduleId: number): Promise<QueuePosition> =>
      practiceSessionId
        ? api.practiceQueuePosition(practiceSessionId, scheduleId)
        : api.queuePosition(scheduleId),

    /**
     * 대기 세션 만료를 방지하기 위해 갱신 주기(Heartbeat) 신호를 보냅니다.
     */
    queueHeartbeat: (scheduleId: number): Promise<void> =>
      practiceSessionId
        ? api.practiceQueueHeartbeat(practiceSessionId, scheduleId)
        : api.queueHeartbeat(scheduleId),

    /**
     * 대기 순번 완료 시 예매 단계 입장을 처리하고 토큰 정보를 반환합니다.
     */
    enterQueue: (scheduleId: number): Promise<EntryTokenResult> =>
      practiceSessionId
        ? api.enterPracticeQueue(practiceSessionId, scheduleId)
        : api.enterQueue(scheduleId),

    /**
     * 좌석 예매를 위한 좌석 등급/구역 정보 목록을 조회합니다.
     */
    sections: (scheduleId: number, entryToken: string): Promise<SeatSection[]> =>
      practiceSessionId
        ? api.practiceSections(practiceSessionId, scheduleId, entryToken)
        : api.sections(scheduleId, entryToken),

    /**
     * 특정 구역에 속한 좌석 레이아웃 및 점유 상태 목록을 조회합니다.
     */
    seats: (scheduleId: number, sectionId: number, entryToken: string): Promise<Seat[]> =>
      practiceSessionId
        ? api.practiceSeats(practiceSessionId, scheduleId, sectionId, entryToken)
        : api.seats(scheduleId, sectionId, entryToken),

    /**
     * 선택한 좌석들에 대해 일시적인 선점(Hold) 처리를 요청합니다.
     */
    holdSeats: (scheduleId: number, seatIds: number[], entryToken: string): Promise<SeatHold> =>
      api.holdSeats(scheduleId, seatIds, entryToken),

    /**
     * 선점된 좌석을 기반으로 공식 예매 데이터를 생성합니다.
     */
    createReservation: (holdId: string, entryToken: string): Promise<Reservation> =>
      practiceSessionId
        ? api.createPracticeReservation(holdId, entryToken)
        : api.createReservation(holdId, entryToken),

    /**
     * 예매 상세 정보 및 진행 상태를 조회합니다.
     */
    reservation: (reservationId: number): Promise<Reservation> =>
      practiceSessionId
        ? api.practiceReservation(practiceSessionId, reservationId)
        : api.reservation(reservationId),

    /**
     * 결제 대기 레코드를 생성하고 결제 과정을 준비합니다.
     */
    requestPayment: (reservationId: number): Promise<PaymentRequest> =>
      practiceSessionId
        ? api.requestPracticePayment(practiceSessionId, reservationId)
        : api.requestPayment(reservationId),

    /**
     * 외부 PG 연동을 모사하여 결제 처리 준비 상태를 조회합니다.
     */
    paymentPreparation: (paymentId: string): Promise<PaymentPreparation> =>
      practiceSessionId
        ? api.practicePaymentPreparation(practiceSessionId, paymentId)
        : api.paymentPreparation(paymentId),

    /**
     * 결제 모킹 처리 완료 상태(성공 혹은 실패)를 리포트합니다.
     */
    completePayment: (paymentId: string, result: 'SUCCESS' | 'FAILED'): Promise<Payment> =>
      practiceSessionId
        ? api.completePracticePayment(practiceSessionId, paymentId, result)
        : api.completePayment(paymentId, result),
  }
}
