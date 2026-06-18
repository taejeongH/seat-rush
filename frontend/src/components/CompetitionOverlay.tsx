import type { CompetitionSnapshot } from '../competition'

interface CompetitionOverlayProps {
  snapshot: CompetitionSnapshot
  connected: boolean
  onOpen: () => void
}

const statusMap: Record<string, string> = {
  IDLE: '대기 중',
  PREPARING: '준비 중',
  READY: '준비 완료 (대기)',
  WAITING: '진행 중 (대기열)',
  COMPLETED: '완료됨',
  FAILED: '실패',
}

/**
 * 일반 예매 흐름 진행 중에 하단 구석에 떠 있으며, 가상 사용자 경쟁 진행 현황을 간략하게 보여주는 오버레이 컴포넌트입니다.
 */
export function CompetitionOverlay({
  snapshot,
  connected,
  onOpen,
}: CompetitionOverlayProps) {
  const confirmed = snapshot.userStatuses.CONFIRMED ?? 0
  const abandoned = ['ABANDONED_QUEUE', 'ABANDONED_ENTRY', 'ABANDONED_HOLD']
    .reduce((total, status) => total + (snapshot.userStatuses[status] ?? 0), 0)
  const failed = ['FAILED', 'PAYMENT_FAILED']
    .reduce((total, status) => total + (snapshot.userStatuses[status] ?? 0), 0)

  return (
    <button className="competition-overlay" onClick={onOpen}>
      <span className={`competition-live-dot ${connected ? 'connected' : ''}`} />
      <span>
        <strong>가상 사용자 경쟁 현황</strong>
        <small>
          {statusMap[snapshot.status] ?? snapshot.status} · 완료 {snapshot.completedUsers}/{snapshot.totalUsers}
        </small>
      </span>
      <span className="competition-overlay-stats">
        <b>성공 {confirmed}</b>
        <b>이탈 {abandoned}</b>
        <b>실패 {failed}</b>
      </span>
    </button>
  )
}
