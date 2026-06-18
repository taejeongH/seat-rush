import { RefreshCw } from 'lucide-react'
import type { QueuePosition } from '../types'

interface QueueViewProps {
  queue: QueuePosition
  actionLoading: boolean
  onEnter: () => void
}

/**
 * 대기열 방 화면으로, 실시간 순번과 입장 허용(ENTERABLE) 시 입장 버튼을 렌더링하는 컴포넌트입니다.
 */
export function QueueView({ queue, actionLoading, onEnter }: QueueViewProps) {
  const enterable = queue.status === 'ENTERABLE'
  const progress = enterable
    ? 100
    : Math.max(8, 100 - (queue.position / Math.max(queue.totalWaiting, 1)) * 100)

  return (
    <div className="queue-stage">
      <p className="eyebrow">{enterable ? 'Your turn' : 'Waiting room'}</p>
      <h2>
        {enterable
          ? '지금 입장하여 좌석을 예약할 수 있습니다!'
          : '예매 대기열에서 접속 순서를 기다리고 있습니다'}
      </h2>
      <div className="queue-number">
        {enterable ? 'GO' : queue.position.toLocaleString()}
      </div>
      <p className="muted">
        {enterable
          ? '좌석 선택 화면으로 이동하려면 아래 버튼을 눌러주세요.'
          : queue.position <= 1
            ? '곧 입장하실 차례입니다. 잠시만 더 기다려주세요!'
            : `내 앞에 ${Math.max(0, queue.position - 1).toLocaleString()}명이 대기 중입니다.`}
      </p>
      <div className="queue-track">
        <span style={{ width: `${progress}%` }} />
      </div>
      {enterable ? (
        <button
          className="primary-button"
          onClick={onEnter}
          disabled={actionLoading}
        >
          {actionLoading ? '좌석 선택 화면 이동 중...' : '좌석 선택하기'}
        </button>
      ) : (
        <div className="meta-row">
          <RefreshCw size={15} className="spinner-icon" />
          <span>순번을 자동으로 갱신하고 있습니다.</span>
        </div>
      )}
    </div>
  )
}
