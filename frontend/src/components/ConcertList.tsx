import { MapPin } from 'lucide-react'
import type { Concert } from '../types'
import { EmptyState } from './Common'

const posterFallbacks = [
  'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=85',
  'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?auto=format&fit=crop&w=1200&q=85',
  'https://images.unsplash.com/photo-1468359601543-843bfaef291a?auto=format&fit=crop&w=1200&q=85',
]

interface ConcertListProps {
  concerts: Concert[]
  onSelect: (concert: Concert) => void
}

/**
 * 예매 대기 중인 모든 공연 리스트 카드를 바둑판 형태로 보여주는 컴포넌트입니다.
 */
export function ConcertList({ concerts, onSelect }: ConcertListProps) {
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Now booking</p>
          <h1>오늘의 무대를<br />가장 먼저 만나세요</h1>
          <p className="muted">예매하실 공연을 선택해 주세요.</p>
        </div>
      </div>
      {concerts.length ? (
        <div className="concert-grid">
          {concerts.map((item, index) => (
            <article className="concert-card" key={item.concertId}>
              <img
                className="poster"
                src={item.posterUrl}
                alt={`${item.title} 포스터`}
                onError={(event) => {
                  event.currentTarget.src = posterFallbacks[index % posterFallbacks.length]
                }}
              />
              <div className="concert-body">
                <h3>{item.title}</h3>
                <div className="meta-row">
                  <MapPin size={15} />
                  <span>{item.venue}</span>
                </div>
                <button className="primary-button" onClick={() => onSelect(item)}>
                  공연 상세 및 예매
                </button>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState message="현재 예매 진행 중인 공연이 없습니다." />
      )}
    </>
  )
}
