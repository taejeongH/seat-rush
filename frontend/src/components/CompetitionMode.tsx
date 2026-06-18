import { useEffect, useState } from 'react'
import {
  Activity, ArrowLeft, CircleStop, Play, RefreshCw, Server,
  Trophy, Users,
} from 'lucide-react'
import { api } from '../api'
import {
  competitionApi,
  type CompetitionSnapshot,
} from '../competition'
import type { Schedule, SeatLayout } from '../types'

/**
 * 가상 사용자 경쟁 엔진의 글로벌 상태(STATUS)를 친근한 한국어 텍스트로 치환하기 위한 매핑 사전입니다.
 */
const competitionStatusLabel: Record<string, string> = {
  IDLE: '대기 중',
  PREPARING: '사용자 로그인 준비 중',
  READY: '대기열 오픈 대기 중 (카운트다운)',
  WAITING: '예매 대기열 진행 중',
  COMPLETED: '예매 종료됨',
  FAILED: '시스템 실패',
}

/**
 * 가상 사용자의 개별 행동 단계 상태값을 직관적인 한국어 텍스트로 치환하기 위한 매핑 사전입니다.
 */
const eventStatusLabel: Record<string, string> = {
  PREPARING: '로그인 준비',
  READY: '준비 완료',
  WAITING: '대기열 진입',
  ENTERED: '좌석 선택 단계 입장',
  HELD: '좌석 선점',
  RESERVED: '예매 신청',
  CONFIRMED: '결제 완료(예매 성공)',
  FAILED: '예매 실패',
  PAYMENT_FAILED: '결제 실패',
  ABANDONED_QUEUE: '대기 중 이탈',
  ABANDONED_ENTRY: '입장 후 이탈',
  ABANDONED_HOLD: '선점 후 이탈',
}

/**
 * 가상 사용자 생성기가 보내오는 영어 메시지 로그를 읽기 쉽도록 한국어로 번역 및 정리합니다.
 */
const translateEventDetail = (detail: string) => {
  if (!detail) return '-'
  return detail
    .replace(/waiting rank: (\d+)/g, '대기 순번: $1번')
    .replace(/held seatIds: ([\d,]+)/g, '좌석 선점: $1번')
    .replace(/reservationId: (\d+)/g, '예매 번호: $1')
    .replace(/paymentId: ([\w-]+)/g, '결제 ID: $1')
    .replace(/abandoned after entry token expired/g, '입장 토큰 만료로 이탈')
    .replace(/abandoned queue/g, '대기열 진입 포기')
    .replace(/abandoned after hold expired/g, '좌석 선점 만료로 이탈')
}

const formatDateTime = (value?: string | null) => value
  ? new Intl.DateTimeFormat('ko-KR', {
      month: 'numeric',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value))
  : '-'

const sumStatuses = (
  statuses: Record<string, number>,
  keys: string[],
) => keys.reduce((total, key) => total + (statuses[key] ?? 0), 0)

export function CompetitionMode({
  snapshot,
  connected,
  onSnapshot,
  onStarted,
  onBack,
}: {
  snapshot: CompetitionSnapshot
  connected: boolean
  onSnapshot: (snapshot: CompetitionSnapshot) => void
  onStarted: (
    schedule: Schedule,
    practiceSessionId: string
  ) => Promise<void>
  onBack: () => void
}) {
  const [virtualUsers, setVirtualUsers] = useState(100)
  const [prepareConcurrency, setPrepareConcurrency] = useState(50)
  const [joinJitterMillis, setJoinJitterMillis] = useState(0)
  const [startDelaySeconds, setStartDelaySeconds] = useState(60)
  const [practiceDurationMinutes, setPracticeDurationMinutes] = useState(30)
  const [seatLayouts, setSeatLayouts] = useState<SeatLayout[]>([])
  const [seatLayoutId, setSeatLayoutId] = useState<number | null>(null)
  const [behaviors, setBehaviors] = useState({
    abandonQueue: 5,
    abandonAfterEntry: 5,
    abandonAfterHold: 10,
    paymentFailure: 10,
    paymentSuccess: 70,
  })
  const [storedAccounts, setStoredAccounts] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        const [pool, layouts] = await Promise.all([
          competitionApi.accountPool(),
          api.seatLayouts(),
        ])
        setStoredAccounts(pool.storedAccounts)
        setSeatLayouts(layouts)
        setSeatLayoutId((current) => current ?? layouts[0]?.seatLayoutId ?? null)
        setMessage('')
      } catch {
        setMessage('로컬 가상 사용자 생성기를 먼저 실행해주세요.')
      }
    }

    void loadInitialData()
  }, [])

  const behaviorTotal = Object.values(behaviors)
    .reduce((total, value) => total + value, 0)
  const running = ['PREPARING', 'READY', 'WAITING'].includes(snapshot.status)
  const confirmed = snapshot.userStatuses.CONFIRMED ?? 0
  const abandoned = sumStatuses(snapshot.userStatuses, [
    'ABANDONED_QUEUE',
    'ABANDONED_ENTRY',
    'ABANDONED_HOLD',
  ])
  const failed = sumStatuses(snapshot.userStatuses, ['FAILED', 'PAYMENT_FAILED'])
  const waiting = sumStatuses(snapshot.userStatuses, [
    'PREPARING',
    'READY',
    'WAITING',
    'ENTERED',
    'HELD',
    'RESERVED',
  ])
  const progress = snapshot.totalUsers
    ? Math.round((snapshot.completedUsers / snapshot.totalUsers) * 100)
    : 0

  const start = async () => {
    const selectedLayout = seatLayouts.find((layout) => layout.seatLayoutId === seatLayoutId)
    if (!selectedLayout || behaviorTotal !== 100) return
    setSubmitting(true)
    setMessage('')
    try {
      const practiceSessionId = crypto.randomUUID()
      const nextSnapshot = await competitionApi.start({
        seatLayoutId: selectedLayout.seatLayoutId,
        practiceSessionId,
        virtualUsers,
        countdownSeconds: startDelaySeconds,
        practiceDurationMinutes,
        prepareConcurrency,
        joinJitterMillis,
        behaviors,
      })
      onSnapshot(nextSnapshot)
      setStoredAccounts((await competitionApi.accountPool()).storedAccounts)
      const bookingOpenAt = new Date(Date.now() + startDelaySeconds * 1000).toISOString()
      const bookingCloseAt = new Date(
        Date.now() + (startDelaySeconds + practiceDurationMinutes * 60) * 1000,
      ).toISOString()
      const practiceSchedule: Schedule = {
        scheduleId: selectedLayout.seatLayoutId,
        performanceAt: bookingCloseAt,
        bookingOpenAt,
        bookingCloseAt,
        status: 'UPCOMING',
      }
      await onStarted(practiceSchedule, practiceSessionId)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '경쟁을 시작하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  const stop = async () => {
    setSubmitting(true)
    try {
      await competitionApi.stop()
      if (snapshot.practiceSessionId) {
        await Promise.allSettled([
          api.deletePracticeSession(snapshot.practiceSessionId),
          api.deletePracticeQueueSession(snapshot.practiceSessionId),
        ])
      }
      onSnapshot(await competitionApi.status())
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '경쟁을 중지하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="competition-page">
      <div className="competition-heading">
        <div>
          <button className="ghost-button" onClick={onBack}>
            <ArrowLeft size={17} /> 공연 목록
          </button>
          <p className="eyebrow competition-eyebrow">Competition mode</p>
          <h1>가상 사용자와 티켓을 두고 경쟁하세요</h1>
          <p className="muted">
            실제 사용자와 가상 사용자가 같은 오픈 시각에 대기열로 진입합니다.
          </p>
        </div>
        <div className={`generator-status ${connected ? 'connected' : ''}`}>
          <Server size={17} />
          <span>{connected ? '생성기 연결됨' : '생성기 연결 필요'}</span>
        </div>
      </div>

      {message && <div className="error-banner">{message}</div>}

      <div className="competition-layout">
        <section className="competition-control">
          <div className="panel-title">
            <div><p className="eyebrow">Match setup</p><h2>경쟁 설정</h2></div>
            <span className="account-count"><Users size={15} /> 저장 계정 {storedAccounts.toLocaleString()}명</span>
          </div>

          <div className="competition-form">
            <div className="practice-template-note">
              <strong>가상 연습 회차</strong>
              <span>기존 좌석 구조를 템플릿으로 사용하고, 대기열과 예매 결과는 연습 데이터로만 분리됩니다.</span>
            </div>

            <label className="form-field">
              <span>좌석 배치</span>
              <select value={seatLayoutId ?? ''} disabled={running}
                onChange={(event) => setSeatLayoutId(Number(event.target.value))}>
                {seatLayouts.map((layout) => (
                  <option key={layout.seatLayoutId} value={layout.seatLayoutId}>
                    {layout.name} · {layout.totalSeatCount.toLocaleString()}석
                  </option>
                ))}
              </select>
            </label>

            <div className="competition-input-grid">
              <NumberField label="가상 사용자" value={virtualUsers} min={1} max={10000}
                disabled={running} onChange={setVirtualUsers} />
              <NumberField label="계정 준비 동시성" value={prepareConcurrency} min={1} max={500}
                disabled={running} onChange={setPrepareConcurrency} />
              <NumberField label="준비 완료 후 카운트다운(초)" value={startDelaySeconds} min={0} max={3600}
                disabled={running} onChange={setStartDelaySeconds} />
              <NumberField label="연습 오픈 시간(분)" value={practiceDurationMinutes} min={1} max={180}
                disabled={running} onChange={setPracticeDurationMinutes} />
              <NumberField label="진입 분산 시간(ms)" value={joinJitterMillis} min={0} max={30000}
                disabled={running} onChange={setJoinJitterMillis} />
            </div>

            <fieldset className="behavior-fieldset" disabled={running}>
              <legend>가상 사용자 행동 비율</legend>
              <div className="behavior-grid">
                {[
                  ['abandonQueue', '대기 중 이탈'],
                  ['abandonAfterEntry', '입장 후 이탈'],
                  ['abandonAfterHold', '선점 후 이탈'],
                  ['paymentFailure', '결제 실패'],
                  ['paymentSuccess', '결제 성공'],
                ].map(([key, label]) => (
                  <NumberField key={key} label={label}
                    value={behaviors[key as keyof typeof behaviors]} min={0} max={100}
                    onChange={(value) => setBehaviors((current) => ({
                      ...current,
                      [key]: value,
                    }))} />
                ))}
              </div>
              <div className={`behavior-total ${behaviorTotal === 100 ? 'valid' : ''}`}>
                합계 {behaviorTotal}% {behaviorTotal !== 100 && '· 100%로 맞춰주세요'}
              </div>
            </fieldset>

            <div className="competition-actions">
              {running ? (
                <button className="danger-button" disabled={submitting} onClick={() => void stop()}>
                  <CircleStop size={17} /> 경쟁 중지
                </button>
              ) : (
                <button className="primary-button"
                  disabled={!connected || !seatLayoutId || behaviorTotal !== 100 || submitting}
                  onClick={() => void start()}>
                  <Play size={17} /> 경쟁 시작
                </button>
              )}
            </div>
          </div>
        </section>

        <section className="competition-dashboard">
          <div className="panel-title">
            <div><p className="eyebrow">Live race</p><h2>실시간 경쟁 현황</h2></div>
            <span className="run-badge">
              <Activity size={15} /> {competitionStatusLabel[snapshot.status] ?? snapshot.status}
            </span>
          </div>

          <div className="competition-progress">
            <div><strong>{progress}%</strong><span>{snapshot.completedUsers.toLocaleString()} / {snapshot.totalUsers.toLocaleString()}</span></div>
            <div className="queue-track"><span style={{ width: `${progress}%` }} /></div>
          </div>

          <div className="competition-metrics">
            <Metric icon={<Trophy size={18} />} label="예매 성공" value={confirmed} tone="success" />
            <Metric icon={<RefreshCw size={18} />} label="진행 중" value={waiting} />
            <Metric icon={<ArrowLeft size={18} />} label="이탈" value={abandoned} tone="muted" />
            <Metric icon={<CircleStop size={18} />} label="실패" value={failed} tone="danger" />
          </div>

          <div className="competition-events">
            <h3>최근 움직임</h3>
            {snapshot.recentEvents.length ? snapshot.recentEvents.slice(0, 12).map((event, index) => (
              <div className="competition-event" key={`${event.userNumber}-${event.occurredAt}-${index}`}>
                <span className="event-user">#{event.userNumber}</span>
                <strong>{eventStatusLabel[event.status] ?? event.status}</strong>
                <span>{translateEventDetail(event.detail)}</span>
                <time>{formatDateTime(event.occurredAt)}</time>
              </div>
            )) : (
              <div className="competition-empty">
                경쟁을 시작하면 가상 사용자들의 움직임이 여기에 표시됩니다.
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

function NumberField({
  label,
  value,
  min,
  max,
  disabled = false,
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  disabled?: boolean
  onChange: (value: number) => void
}) {
  return (
    <label className="form-field">
      <span>{label}</span>
      <input type="number" value={value} min={min} max={max} disabled={disabled}
        onChange={(event) => onChange(Number(event.target.value))} />
    </label>
  )
}

function Metric({
  icon,
  label,
  value,
  tone = '',
}: {
  icon: React.ReactNode
  label: string
  value: number
  tone?: string
}) {
  return (
    <div className={`competition-metric ${tone}`}>
      <span>{icon}{label}</span>
      <strong>{value.toLocaleString()}</strong>
    </div>
  )
}
