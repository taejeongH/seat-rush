import type { Reservation, SeatHold } from '../types'

interface PaymentViewProps {
  reservation: Reservation
  hold: SeatHold | null
  preparation: 'IDLE' | 'PROCESSING' | 'READY' | 'SUCCESS' | 'FAILED'
  actionLoading: boolean
  onPay: (result: 'SUCCESS' | 'FAILED') => void
}

const formatDate = (value?: string | null) => value
  ? new Intl.DateTimeFormat('ko-KR', {
      month: 'long', day: 'numeric', weekday: 'short',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    }).format(new Date(value))
  : '-'

const formatPrice = (value: number) =>
  new Intl.NumberFormat('ko-KR').format(value) + '원'

interface InfoRowProps {
  label: string
  value: string
  strong?: boolean
}

function InfoRow({ label, value, strong = false }: InfoRowProps) {
  return (
    <div className="reservation-row">
      <span>{label}</span>
      <strong className={strong ? 'price' : ''}>{value}</strong>
    </div>
  )
}

/**
 * 예매 정보 확인 및 Mock 결제(성공/실패) 단계를 처리하는 컴포넌트입니다.
 */
export function PaymentView({
  reservation,
  hold,
  preparation,
  actionLoading,
  onPay
}: PaymentViewProps) {
  const ready = preparation === 'READY'

  return (
    <>
      <p className="eyebrow">Payment</p>
      <h2>예매 상세 정보를 확인하세요</h2>
      <p className="muted">
        {ready
          ? '선점 만료 시간 전까지 아래 Mock 결제를 완료해 주세요.'
          : '결제 모사 처리를 연동하고 있습니다. 잠시만 대기해 주세요.'}
      </p>
      <div className="reservation-box">
        <InfoRow label="예매 번호" value={String(reservation.reservationId)} />
        <InfoRow
          label="선택 좌석"
          value={reservation.seats
            .map((seat) => `${seat.sectionName}등급 ${seat.rowName}열 ${seat.seatNumber}번`).join(', ')}
        />
        <InfoRow label="좌석 선점 만료 시각" value={formatDate(hold?.expiresAt)} />
        <InfoRow label="최종 결제 금액" value={formatPrice(reservation.totalAmount)} strong />
      </div>
      <div className="payment-actions">
        <button
          className="danger-button"
          disabled={!ready || actionLoading}
          onClick={() => onPay('FAILED')}
        >
          Mock 결제 실패
        </button>
        <button
          className="primary-button"
          disabled={!ready || actionLoading}
          onClick={() => onPay('SUCCESS')}
        >
          {actionLoading
            ? '결제 처리 중...'
            : ready
              ? 'Mock 결제 성공'
              : '결제 요청 준비 중...'}
        </button>
      </div>
    </>
  )
}
