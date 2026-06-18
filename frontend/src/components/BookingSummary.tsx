import { MapPin } from 'lucide-react'
import type { ConcertDetail, Reservation, Schedule } from '../types'

const posterFallbacks = [
  'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=85',
]

const formatDate = (value?: string) => value
  ? new Intl.DateTimeFormat('ko-KR', {
      month: 'long', day: 'numeric', weekday: 'short',
      hour: '2-digit', minute: '2-digit',
    }).format(new Date(value))
  : '-'

const formatPrice = (value: number) =>
  new Intl.NumberFormat('ko-KR').format(value) + '원'

interface BookingSummaryProps {
  concert: ConcertDetail | null
  schedule: Schedule | null
  reservation: Reservation | null
}

/**
 * 화면 오른쪽에 항상 붙어 있으며, 선택한 공연 정보와 예매 단계별 정보를 실시간으로 요약해 보여주는 컴포넌트입니다.
 */
export function BookingSummary({ concert, schedule, reservation }: BookingSummaryProps) {
  if (!concert) return null

  return (
    <aside className="summary-panel">
      <img
        className="summary-poster"
        src={concert.posterUrl}
        alt={`${concert.title} 포스터 요약`}
        onError={(event) => {
          event.currentTarget.src = posterFallbacks[0]
        }}
      />
      <h3>{concert.title}</h3>
      <div className="meta-row">
        <MapPin size={14} />
        <span>{concert.venue}</span>
      </div>
      <ul className="summary-list">
        <li>
          <span>관람 일시</span>
          <strong>{formatDate(schedule?.performanceAt)}</strong>
        </li>
        {reservation && (
          <>
            <li>
              <span>선택 좌석</span>
              <strong>{reservation.seats.length}석</strong>
            </li>
            <li>
              <span>결제 금액</span>
              <strong>{formatPrice(reservation.totalAmount)}</strong>
            </li>
          </>
        )}
      </ul>
    </aside>
  )
}
