import type { Seat, SeatSection } from '../types'

interface SeatSelectionProps {
  sections: SeatSection[]
  selectedSection: SeatSection | null
  seats: Seat[]
  selectedSeatIds: number[]
  total: number
  actionLoading: boolean
  onSection: (section: SeatSection) => void
  onSeat: (seat: Seat) => void
  onReserve: () => void
}

const formatPrice = (value: number) =>
  new Intl.NumberFormat('ko-KR').format(value) + '원'

/**
 * 구역 및 좌석 그리드 레이아웃을 통해 실시간 좌석 선택 단계를 제공하는 컴포넌트입니다.
 */
export function SeatSelection({
  sections,
  selectedSection,
  seats,
  selectedSeatIds,
  total,
  actionLoading,
  onSection,
  onSeat,
  onReserve
}: SeatSelectionProps) {
  return (
    <>
      <p className="eyebrow">Seat map</p>
      <h2>원하시는 좌석을 선택하세요</h2>
      <p className="muted">한 번의 예매 단계에서 최대 4석까지 동시 예매가 가능합니다.</p>

      <div className="section-list">
        {sections.map((item) => (
          <button
            className={`section-item ${selectedSection?.sectionId === item.sectionId ? 'selected' : ''}`}
            key={item.sectionId}
            onClick={() => onSection(item)}
          >
            <strong>{item.name}석</strong>
            <span>{formatPrice(item.price)}</span>
          </button>
        ))}
      </div>

      <div className="seat-toolbar">
        <strong>{selectedSection?.name ?? '-'} 등급 구역</strong>
        <div className="seat-legend">
          <span><i className="legend-dot available" />선택 가능</span>
          <span><i className="legend-dot selected" />선택함</span>
          <span><i className="legend-dot unavailable" />선택 불가 (선점됨)</span>
        </div>
      </div>

      <div className="stage">STAGE</div>

      <div className="seat-map-container" style={{ overflowX: 'auto', paddingBottom: '12px' }}>
        <div className="seat-map" style={{ minWidth: 'fit-content' }}>
          {seats.map((seat) => {
            const selected = selectedSeatIds.includes(seat.seatId)
            const unavailable = seat.status !== 'AVAILABLE'
            return (
              <button
                className={`seat ${selected ? 'selected' : ''} ${unavailable ? 'unavailable' : ''}`}
                disabled={unavailable}
                key={seat.seatId}
                title={`${seat.rowName}열 ${seat.seatNumber}번`}
                aria-label={`${seat.rowName}열 ${seat.seatNumber}번 좌석`}
                onClick={() => onSeat(seat)}
              />
            )
          })}
        </div>
      </div>

      <div className="selection-footer">
        <div>
          <span className="muted">{selectedSeatIds.length}개 좌석 선택됨</span>
          <div className="price">{formatPrice(total)}</div>
        </div>
        <button
          className="primary-button"
          disabled={!selectedSeatIds.length || actionLoading}
          onClick={onReserve}
        >
          {actionLoading ? '좌석 선점 처리 중...' : '좌석 선점하기'}
        </button>
      </div>
    </>
  )
}
