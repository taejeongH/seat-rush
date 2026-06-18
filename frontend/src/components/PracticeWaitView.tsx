import { Ticket, UserRound } from 'lucide-react'
import type { Schedule } from '../types'
import type { CompetitionSnapshot } from '../competition'

interface PracticeWaitViewProps {
  snapshot: CompetitionSnapshot
  schedule: Schedule
  countdownMillis: number
  loggedIn: boolean
  actionLoading: boolean
  onLogin: () => void
  onJoin: () => void
}

const formatDate = (value?: string | null) => value
  ? new Intl.DateTimeFormat('ko-KR', {
      month: 'long', day: 'numeric', weekday: 'short',
      hour: '2-digit', minute: '2-digit',
    }).format(new Date(value))
  : '-'

const formatCountdown = (millis: number) => {
  const totalSeconds = Math.max(0, Math.ceil(millis / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

/**
 * 연습용 세션 시작 시 가상 사용자 로그인 시뮬레이션 및 카운트다운을 표시하며
 * 입장 오픈 타이밍에 맞춰 입장 액션을 처리하는 대기 화면 컴포넌트입니다.
 */
export function PracticeWaitView({
  snapshot,
  schedule,
  countdownMillis,
  loggedIn,
  actionLoading,
  onLogin,
  onJoin,
}: PracticeWaitViewProps) {
  const ready = snapshot.userStatuses.READY ?? 0
  const preparing = snapshot.userStatuses.PREPARING ?? 0
  const total = snapshot.totalUsers
  const readyRatio = total ? Math.round((ready / total) * 100) : 0

  // 카운트다운(스케줄 준비 시작 시간 설정) 여부 판정
  const countdownStarted = Boolean(snapshot.startAt)
  // 카운트다운이 종료되었는지 여부
  const startNow = countdownStarted && countdownMillis <= 0
  const displayedOpenAt = snapshot.startAt ?? schedule.bookingOpenAt

  return (
    <div className="practice-wait-stage">
      <p className="eyebrow">Practice mode</p>
      <h2>
        {startNow
          ? '연습 회차가 열렸습니다!'
          : countdownStarted
            ? '연습 티켓팅 시작을 기다리고 있습니다'
            : '가상 사용자를 준비하고 있습니다'}
      </h2>
      <div className="practice-countdown">
        {countdownStarted ? formatCountdown(countdownMillis) : 'READY'}
      </div>
      <p className="muted">
        모든 가상 사용자의 로그인 연동이 완료되면 1분 카운트다운 후 실시간 대기열이 오픈됩니다.
      </p>

      <div className="practice-schedule-card">
        <span>가상 연습 회차</span>
        <strong>{formatDate(displayedOpenAt)}</strong>
        <small>
          {startNow
            ? '입장 가능 (예매 시작됨)'
            : countdownStarted
              ? '오픈 대기 중'
              : '가상 사용자 준비 중'}
        </small>
      </div>

      <div className="practice-ready-panel">
        <div>
          <span>가상 사용자 준비 상태</span>
          <strong>{ready.toLocaleString()} / {total.toLocaleString()}</strong>
        </div>
        <div className="queue-track">
          <span style={{ width: `${readyRatio}%` }} />
        </div>
        <small>
          {preparing > 0
            ? `${preparing.toLocaleString()}명의 가상 사용자가 로그인 준비 중입니다.`
            : '모든 가상 사용자의 연동이 준비되었습니다. 시작 시간을 대기합니다.'}
        </small>
      </div>

      {!loggedIn ? (
        <button className="primary-button" onClick={onLogin}>
          <UserRound size={17} /> 로그인하고 같이 입장하기
        </button>
      ) : (
        <button
          className={`primary-button ${startNow ? 'pulse-button' : ''}`}
          disabled={!startNow || actionLoading}
          onClick={onJoin}
          style={startNow ? {
            animation: 'pulse 1.5s infinite',
            boxShadow: '0 0 0 0 rgba(232, 71, 45, 0.7)'
          } : {}}
        >
          <Ticket size={17} />
          {actionLoading
            ? '대기열 입장 요청 중입니다...'
            : startNow
              ? '연습 회차 입장하기'
              : countdownStarted
                ? `연습 오픈 대기 중 (${formatCountdown(countdownMillis)})`
                : '가상 사용자 준비 대기 중...'}
        </button>
      )}
    </div>
  )
}
