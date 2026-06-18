import { Check, X } from 'lucide-react'
import type { Reservation } from '../types'

interface ResultViewProps {
  reservation: Reservation
  onReset: () => void
}

/**
 * 최종 예매 완료 결과 상태 혹은 결제 실패 상태를 보여주는 컴포넌트입니다.
 */
export function ResultView({ reservation, onReset }: ResultViewProps) {
  const success = reservation.status === 'CONFIRMED'

  return (
    <div className="result-stage">
      <div className={`result-icon ${success ? '' : 'failed'}`}>
        {success ? <Check size={34} /> : <X size={34} />}
      </div>
      <p className="eyebrow">Reservation result</p>
      <h2>
        {success
          ? '예매가 성공적으로 완료되었습니다!'
          : '결제가 완료되지 않았습니다.'}
      </h2>
      <p className="muted">
        {success
          ? `예매 번호: ${reservation.reservationId} (상태: ${reservation.status})`
          : '결제 대기 시간이 만료되었거나 에러가 발생하여 예매 처리가 취소되었습니다.'}
      </p>
      <button className="primary-button" onClick={onReset}>
        다른 공연 보기
      </button>
    </div>
  )
}
