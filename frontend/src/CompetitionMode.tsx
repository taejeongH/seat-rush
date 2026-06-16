import { useEffect, useMemo, useState } from 'react'
import {
  Activity, ArrowLeft, CircleStop, Play, RefreshCw, Server,
  Trophy, Users,
} from 'lucide-react'
import { api } from './api'
import {
  competitionApi,
  type CompetitionSnapshot,
} from './competition'
import type { Concert, Schedule } from './types'

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
  concerts,
  snapshot,
  connected,
  onSnapshot,
  onStarted,
  onBack,
}: {
  concerts: Concert[]
  snapshot: CompetitionSnapshot
  connected: boolean
  onSnapshot: (snapshot: CompetitionSnapshot) => void
  onStarted: (concertId: number, schedule: Schedule) => Promise<void>
  onBack: () => void
}) {
  const [concertId, setConcertId] = useState('')
  const [scheduleId, setScheduleId] = useState('')
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [virtualUsers, setVirtualUsers] = useState(100)
  const [prepareConcurrency, setPrepareConcurrency] = useState(50)
  const [joinJitterMillis, setJoinJitterMillis] = useState(1000)
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
    const loadAccountPool = async () => {
      try {
        const pool = await competitionApi.accountPool()
        setStoredAccounts(pool.storedAccounts)
        setMessage('')
      } catch {
        setMessage('로컬 가상 사용자 생성기를 먼저 실행해주세요.')
      }
    }

    void loadAccountPool()
  }, [])

  const selectedSchedule = useMemo(
    () => schedules.find((schedule) => String(schedule.scheduleId) === scheduleId),
    [scheduleId, schedules],
  )

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

  const selectConcert = async (value: string) => {
    setConcertId(value)
    setScheduleId('')
    setSchedules([])
    if (!value) return

    try {
      setSchedules(await api.schedules(Number(value)))
      setMessage('')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '회차 조회에 실패했습니다.')
    }
  }

  const start = async () => {
    if (!selectedSchedule || behaviorTotal !== 100) return
    setSubmitting(true)
    setMessage('')
    try {
      const bookingOpenAt = new Date(selectedSchedule.bookingOpenAt)
      const startAt = bookingOpenAt > new Date() ? bookingOpenAt : new Date()
      const nextSnapshot = await competitionApi.start({
        scheduleId: selectedSchedule.scheduleId,
        virtualUsers,
        startAt: startAt.toISOString(),
        prepareConcurrency,
        joinJitterMillis,
        behaviors,
      })
      onSnapshot(nextSnapshot)
      setStoredAccounts((await competitionApi.accountPool()).storedAccounts)
      await onStarted(Number(concertId), selectedSchedule)
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
            <label className="form-field">
              <span>공연</span>
              <select value={concertId} disabled={running}
                onChange={(event) => void selectConcert(event.target.value)}>
                <option value="">공연을 선택하세요</option>
                {concerts.map((concert) => (
                  <option key={concert.concertId} value={concert.concertId}>
                    {concert.title} · {concert.venue}
                  </option>
                ))}
              </select>
            </label>

            <label className="form-field">
              <span>회차</span>
              <select value={scheduleId} disabled={!concertId || running}
                onChange={(event) => setScheduleId(event.target.value)}>
                <option value="">회차를 선택하세요</option>
                {schedules.map((schedule) => (
                  <option key={schedule.scheduleId} value={schedule.scheduleId}>
                    공연 {formatDateTime(schedule.performanceAt)}
                    {' · '}오픈 {formatDateTime(schedule.bookingOpenAt)}
                  </option>
                ))}
              </select>
            </label>

            <div className="competition-input-grid">
              <NumberField label="가상 사용자" value={virtualUsers} min={1} max={10000}
                disabled={running} onChange={setVirtualUsers} />
              <NumberField label="계정 준비 동시성" value={prepareConcurrency} min={1} max={500}
                disabled={running} onChange={setPrepareConcurrency} />
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
                  disabled={!connected || !selectedSchedule || behaviorTotal !== 100 || submitting}
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
            <span className="run-badge"><Activity size={15} /> {snapshot.status}</span>
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
                <strong>{event.status}</strong>
                <span>{event.detail}</span>
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
