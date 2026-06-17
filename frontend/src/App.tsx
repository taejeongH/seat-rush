import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowLeft, Check, Clock3, Gamepad2, LogOut, MapPin, RefreshCw, Ticket,
  UserRound, X,
} from 'lucide-react'
import { ApiError, api, tokenStorage } from './api'
import { CompetitionMode } from './CompetitionMode'
import {
  competitionApi,
  type CompetitionSnapshot,
} from './competition'
import type {
  Concert, ConcertDetail, QueuePosition, Reservation, Schedule, Seat,
  SeatHold, SeatSection, User,
} from './types'
import './App.css'

type View =
  | 'concerts'
  | 'competition'
  | 'schedule'
  | 'practice-wait'
  | 'queue'
  | 'seats'
  | 'payment'
  | 'result'
type AuthMode = 'login' | 'signup'

const posterFallbacks = [
  'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=85',
  'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?auto=format&fit=crop&w=1200&q=85',
  'https://images.unsplash.com/photo-1468359601543-843bfaef291a?auto=format&fit=crop&w=1200&q=85',
]

const initialCompetitionSnapshot: CompetitionSnapshot = {
  runId: null,
  status: 'IDLE',
  gatewayBaseUrl: '',
  seatLayoutId: null,
  practiceSessionId: null,
  totalUsers: 0,
  completedUsers: 0,
  startAt: null,
  updatedAt: new Date(0).toISOString(),
  userStatuses: {},
  recentEvents: [],
}

const formatDate = (value?: string) => value
  ? new Intl.DateTimeFormat('ko-KR', {
      month: 'long', day: 'numeric', weekday: 'short',
      hour: '2-digit', minute: '2-digit',
    }).format(new Date(value))
  : '-'

const formatScheduleDeadline = (value: string) =>
  new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'numeric', day: 'numeric', weekday: 'short',
    hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))

const formatPrice = (value: number) =>
  new Intl.NumberFormat('ko-KR').format(value) + '원'

const formatCountdown = (millis: number) => {
  const totalSeconds = Math.max(0, Math.ceil(millis / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

const statusLabel: Record<Schedule['status'], string> = {
  UPCOMING: '오픈 예정',
  BOOKING_OPEN: '예매 가능',
  BOOKING_CLOSED: '예매 종료',
  CANCELED: '취소',
}

const getEffectiveScheduleStatus = (schedule: Schedule): Schedule['status'] => {
  if (schedule.status === 'CANCELED') return 'CANCELED'

  const now = Date.now()
  if (now < new Date(schedule.bookingOpenAt).getTime()) return 'UPCOMING'
  if (now >= new Date(schedule.bookingCloseAt).getTime()) return 'BOOKING_CLOSED'
  return 'BOOKING_OPEN'
}

const isScheduleOpen = (schedule: Schedule) =>
  getEffectiveScheduleStatus(schedule) === 'BOOKING_OPEN'

export default function App() {
  const [view, setView] = useState<View>('concerts')
  const [user, setUser] = useState<User | null>(null)
  const [authOpen, setAuthOpen] = useState(false)
  const [concerts, setConcerts] = useState<Concert[]>([])
  const [concert, setConcert] = useState<ConcertDetail | null>(null)
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [schedule, setSchedule] = useState<Schedule | null>(null)
  const [queue, setQueue] = useState<QueuePosition | null>(null)
  const [entryToken, setEntryToken] = useState('')
  const [sections, setSections] = useState<SeatSection[]>([])
  const [section, setSection] = useState<SeatSection | null>(null)
  const [seats, setSeats] = useState<Seat[]>([])
  const [selectedSeatIds, setSelectedSeatIds] = useState<number[]>([])
  const [hold, setHold] = useState<SeatHold | null>(null)
  const [reservation, setReservation] = useState<Reservation | null>(null)
  const [paymentId, setPaymentId] = useState('')
  const [practiceSessionId, setPracticeSessionId] = useState<string | null>(null)
  const [practiceStartAt, setPracticeStartAt] = useState<string | null>(null)
  const [practiceCountdownMillis, setPracticeCountdownMillis] = useState(0)
  const [paymentPreparation, setPaymentPreparation] = useState<
    'IDLE' | 'PROCESSING' | 'READY' | 'SUCCESS' | 'FAILED'
  >('IDLE')
  const paymentInitializationRef = useRef<number | null>(null)
  const autoEnteringScheduleRef = useRef<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [error, setError] = useState('')
  const [competitionSnapshot, setCompetitionSnapshot] =
    useState<CompetitionSnapshot>(initialCompetitionSnapshot)
  const [competitionConnected, setCompetitionConnected] = useState(false)

  const navigate = useCallback((
    nextView: View,
    historyMode: 'push' | 'replace' = 'push',
  ) => {
    const state = { seatRushView: nextView }
    if (historyMode === 'replace') {
      window.history.replaceState(state, '')
    } else {
      window.history.pushState(state, '')
    }
    setView(nextView)
  }, [])

  const handleError = useCallback((caught: unknown) => {
    setError(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해주세요.')
  }, [])

  useEffect(() => {
    window.history.replaceState({ seatRushView: 'concerts' }, '')

    const handlePopState = (event: PopStateEvent) => {
      const nextView = event.state?.seatRushView
      const availableViews: View[] = [
        'concerts', 'competition', 'schedule', 'practice-wait', 'queue', 'seats', 'payment', 'result',
      ]
      setView(availableViews.includes(nextView) ? nextView : 'concerts')
      setError('')
    }

    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    const initialize = async () => {
      try {
        const page = await api.concerts()
        setConcerts(page.content)
        if (tokenStorage.get()) setUser(await api.me())
      } catch (caught) {
        if (!(caught instanceof ApiError) || ![401, 403].includes(caught.status)) {
          handleError(caught)
        }
      } finally {
        setLoading(false)
      }
    }
    void initialize()
  }, [handleError])

  useEffect(() => {
    if (view !== 'practice-wait' || !schedule || !practiceSessionId || !practiceStartAt) {
      return
    }

    const updateCountdown = () => {
      const remaining = new Date(practiceStartAt).getTime() - Date.now()
      setPracticeCountdownMillis(Math.max(0, remaining))

    }

    updateCountdown()
    const timer = window.setInterval(updateCountdown, 250)
    return () => window.clearInterval(timer)
  }, [practiceSessionId, practiceStartAt, schedule, user, view])

  useEffect(() => {
    if (
      !practiceSessionId
      || competitionSnapshot.practiceSessionId !== practiceSessionId
      || !competitionSnapshot.startAt
    ) {
      return
    }

    setPracticeStartAt(competitionSnapshot.startAt)
    setPracticeCountdownMillis(
      Math.max(0, new Date(competitionSnapshot.startAt).getTime() - Date.now()),
    )
  }, [competitionSnapshot.practiceSessionId, competitionSnapshot.startAt, practiceSessionId])

  useEffect(() => {
    let eventSource: EventSource | null = null

    const connectCompetitionGenerator = async () => {
      try {
        setCompetitionSnapshot(await competitionApi.status())
        setCompetitionConnected(true)
        eventSource = new EventSource(competitionApi.eventUrl)
        eventSource.onmessage = (event) => {
          setCompetitionSnapshot(JSON.parse(event.data) as CompetitionSnapshot)
          setCompetitionConnected(true)
        }
        eventSource.onerror = () => setCompetitionConnected(false)
      } catch {
        setCompetitionConnected(false)
      }
    }

    void connectCompetitionGenerator()
    return () => eventSource?.close()
  }, [])

  useEffect(() => {
    if (view !== 'queue' || !schedule || queue?.status === 'ENTERABLE') return
    const timer = window.setInterval(async () => {
      try {
        setQueue(practiceSessionId
          ? await api.practiceQueuePosition(practiceSessionId, schedule.scheduleId)
          : await api.queuePosition(schedule.scheduleId))
      } catch (caught) {
        handleError(caught)
      }
    }, 1500)
    return () => window.clearInterval(timer)
  }, [handleError, view, schedule, queue?.status, practiceSessionId])

  useEffect(() => {
    if (view !== 'queue' || !schedule || queue?.status === 'ENTERABLE') return
    const timer = window.setInterval(() => {
      const heartbeat = practiceSessionId
        ? api.practiceQueueHeartbeat(practiceSessionId, schedule.scheduleId)
        : api.queueHeartbeat(schedule.scheduleId)
      void heartbeat.catch(() => undefined)
    }, 10000)
    return () => window.clearInterval(timer)
  }, [view, schedule, queue?.status, practiceSessionId])

  useEffect(() => {
    if (view !== 'payment' || !reservation) return
    if (paymentInitializationRef.current === reservation.reservationId) return

    paymentInitializationRef.current = reservation.reservationId
    setPaymentPreparation('PROCESSING')

    const prepare = async () => {
      try {
        const requested = practiceSessionId
          ? await api.requestPracticePayment(practiceSessionId, reservation.reservationId)
          : await api.requestPayment(reservation.reservationId)
        setPaymentId(requested.paymentId)

        for (let attempt = 0; attempt < 30; attempt += 1) {
          const preparation = practiceSessionId
            ? await api.practicePaymentPreparation(practiceSessionId, requested.paymentId)
            : await api.paymentPreparation(requested.paymentId)
          setPaymentPreparation(preparation.status)
          if (preparation.status !== 'PROCESSING') return
          await new Promise((resolve) => window.setTimeout(resolve, 400))
        }
        throw new Error('결제 준비 시간이 초과되었습니다.')
      } catch (caught) {
        paymentInitializationRef.current = null
        handleError(caught)
      }
    }

    void prepare()
  }, [handleError, view, reservation, practiceSessionId])

  const selectConcert = async (selected: Concert) => {
    setLoading(true)
    setError('')
    try {
      const [detail, scheduleList] = await Promise.all([
        api.concert(selected.concertId),
        api.schedules(selected.concertId),
      ])
      setConcert(detail)
      setSchedules(scheduleList)
      navigate('schedule')
    } catch (caught) {
      handleError(caught)
    } finally {
      setLoading(false)
    }
  }

  const continueAfterCompetitionStart = async (
    selectedSchedule: Schedule,
    nextPracticeSessionId: string,
  ) => {
    setLoading(true)
    try {
      setConcert({
        concertId: 0,
        title: 'Seat Rush Practice',
        venue: '가상 티켓팅 연습장',
        posterUrl: posterFallbacks[0],
        description: '가상 사용자와 함께 실제 티켓팅 흐름을 연습하는 회차입니다.',
      })
      setSchedules([selectedSchedule])
      setSchedule(selectedSchedule)
      setPracticeSessionId(nextPracticeSessionId)
      setPracticeStartAt(null)
      setPracticeCountdownMillis(0)
      if (!user) setAuthOpen(true)
      navigate('practice-wait')
    } catch (caught) {
      handleError(caught)
    } finally {
      setLoading(false)
    }
  }

  const joinScheduleQueue = async (selected: Schedule) => {
    setActionLoading(true)
    setError('')
    try {
      setQueue(practiceSessionId
        ? await api.joinPracticeQueue(practiceSessionId, selected.scheduleId)
        : await api.joinQueue(selected.scheduleId))
      navigate('queue')
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  const tryJoinPracticeQueue = () => {
    if (!schedule) return
    if (!user) {
      setAuthOpen(true)
      return
    }
    if (!practiceStartAt) {
      window.alert('가상 사용자를 준비하고 있습니다. 잠시 후 다시 시도해주세요.')
      return
    }
    if (Date.now() < new Date(practiceStartAt).getTime()) {
      window.alert('아직 입장 시간이 아닙니다.')
      return
    }
    void joinScheduleQueue(schedule)
  }

  const startQueue = (selected: Schedule) => {
    setSchedule(selected)
    if (!user) {
      setAuthOpen(true)
      return
    }
    if (isScheduleOpen(selected)) void joinScheduleQueue(selected)
  }

  const enter = useCallback(async () => {
    if (!schedule) return
    setActionLoading(true)
    try {
      const issued = practiceSessionId
        ? await api.enterPracticeQueue(practiceSessionId, schedule.scheduleId)
        : await api.enterQueue(schedule.scheduleId)
      const sectionList = practiceSessionId
        ? await api.practiceSections(
            practiceSessionId,
            schedule.scheduleId,
            issued.entryToken,
          )
        : await api.sections(schedule.scheduleId, issued.entryToken)
      setEntryToken(issued.entryToken)
      setSections(sectionList)
      setSection(sectionList[0] ?? null)
      if (sectionList[0]) {
        setSeats(practiceSessionId
          ? await api.practiceSeats(
              practiceSessionId,
              schedule.scheduleId,
              sectionList[0].sectionId,
              issued.entryToken,
            )
          : await api.seats(
              schedule.scheduleId, sectionList[0].sectionId, issued.entryToken,
            ))
      }
      navigate('seats', 'replace')
    } catch (caught) {
      autoEnteringScheduleRef.current = null
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }, [handleError, navigate, schedule])

  useEffect(() => {
    if (
      view !== 'queue'
      || !schedule
      || queue?.status !== 'ENTERABLE'
      || autoEnteringScheduleRef.current === schedule.scheduleId
    ) return

    autoEnteringScheduleRef.current = schedule.scheduleId
    void enter()
  }, [enter, queue?.status, schedule, view])

  const changeSection = async (next: SeatSection) => {
    if (!schedule) return
    setActionLoading(true)
    try {
      setSection(next)
      setSelectedSeatIds([])
      setSeats(practiceSessionId
        ? await api.practiceSeats(
            practiceSessionId,
            schedule.scheduleId,
            next.sectionId,
            entryToken,
          )
        : await api.seats(schedule.scheduleId, next.sectionId, entryToken))
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  const toggleSeat = (seat: Seat) => {
    if (seat.status !== 'AVAILABLE') return
    setSelectedSeatIds((current) =>
      current.includes(seat.seatId)
        ? current.filter((id) => id !== seat.seatId)
        : current.length < 4 ? [...current, seat.seatId] : current,
    )
  }

  const holdAndReserve = async () => {
    if (!schedule || !selectedSeatIds.length) return
    setActionLoading(true)
    try {
      const nextHold = await api.holdSeats(
        schedule.scheduleId, selectedSeatIds, entryToken,
      )
      const nextReservation = practiceSessionId
        ? await api.createPracticeReservation(nextHold.holdId, entryToken)
        : await api.createReservation(nextHold.holdId, entryToken)
      setHold(nextHold)
      setReservation(nextReservation)
      navigate('payment')
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  const pay = async (result: 'SUCCESS' | 'FAILED') => {
    if (!reservation || !paymentId || paymentPreparation !== 'READY') return
    setActionLoading(true)
    setError('')
    try {
      if (practiceSessionId) {
        await api.completePracticePayment(practiceSessionId, paymentId, result)
      } else {
        await api.completePayment(paymentId, result)
      }

      let finalReservation = reservation
      for (let attempt = 0; attempt < 20; attempt += 1) {
        finalReservation = practiceSessionId
          ? await api.practiceReservation(practiceSessionId, reservation.reservationId)
          : await api.reservation(reservation.reservationId)
        if (!['PENDING_PAYMENT', 'PAYMENT_PROCESSING'].includes(finalReservation.status)) {
          break
        }
        await new Promise((resolve) => window.setTimeout(resolve, 500))
      }
      setReservation(finalReservation)
      navigate('result', 'replace')
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  const reset = () => {
    if (practiceSessionId) {
      void Promise.allSettled([
        api.deletePracticeSession(practiceSessionId),
        api.deletePracticeQueueSession(practiceSessionId),
      ])
    }
    navigate('concerts')
    setConcert(null)
    setSchedule(null)
    setQueue(null)
    setEntryToken('')
    setSections([])
    setSection(null)
    setSeats([])
    setSelectedSeatIds([])
    setHold(null)
    setReservation(null)
    setPaymentId('')
    setPracticeSessionId(null)
    setPracticeStartAt(null)
    setPracticeCountdownMillis(0)
    setPaymentPreparation('IDLE')
    paymentInitializationRef.current = null
    autoEnteringScheduleRef.current = null
    setError('')
  }

  const logout = () => {
    tokenStorage.clear()
    setUser(null)
    reset()
  }

  const selectedTotal = useMemo(
    () => selectedSeatIds.length * (section?.price ?? 0),
    [selectedSeatIds, section],
  )

  return (
    <div className="app">
      <header className="topbar">
        <button className="brand" onClick={reset}>
          <span className="brand-mark"><Ticket size={17} /></span>
          Seat Rush
        </button>
        <div className="topbar-actions">
          <button className="ghost-button" onClick={() => navigate('competition')}>
            <Gamepad2 size={17} /> 경쟁 모드
          </button>
          {user ? (
            <>
              <span className="user-name">{user.name}님</span>
              <button className="icon-button" onClick={logout} title="로그아웃">
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <button className="ghost-button" onClick={() => setAuthOpen(true)}>
              <UserRound size={17} /> 로그인
            </button>
          )}
        </div>
      </header>

      <main className="page">
        {error && (
          <div className="error-banner">
            <span>{error}</span>
            <button onClick={() => setError('')} title="오류 닫기"><X size={18} /></button>
          </div>
        )}
        {loading ? <LoadingState /> : view === 'concerts' ? (
          <ConcertList concerts={concerts} onSelect={selectConcert} />
        ) : view === 'competition' ? (
          <CompetitionMode
            snapshot={competitionSnapshot}
            connected={competitionConnected}
            onSnapshot={setCompetitionSnapshot}
            onStarted={continueAfterCompetitionStart}
            onBack={reset}
          />
        ) : (
          <div className="flow-layout">
            <section className="main-panel">
              <FlowSteps view={view} />
              {view === 'schedule' && (
                <ScheduleSelection schedules={schedules} actionLoading={actionLoading}
                  onBack={reset} onSelect={startQueue} />
              )}
              {view === 'practice-wait' && schedule && (
                <PracticeWaitView
                  snapshot={competitionSnapshot}
                  schedule={schedule}
                  countdownMillis={practiceCountdownMillis}
                  loggedIn={Boolean(user)}
                  actionLoading={actionLoading}
                  onLogin={() => setAuthOpen(true)}
                  onJoin={tryJoinPracticeQueue}
                />
              )}
              {view === 'queue' && queue && (
                <QueueView queue={queue} actionLoading={actionLoading} onEnter={enter} />
              )}
              {view === 'seats' && (
                <SeatSelection sections={sections} selectedSection={section} seats={seats}
                  selectedSeatIds={selectedSeatIds} total={selectedTotal}
                  actionLoading={actionLoading} onSection={changeSection}
                  onSeat={toggleSeat} onReserve={holdAndReserve} />
              )}
              {view === 'payment' && reservation && (
                <PaymentView reservation={reservation} hold={hold}
                  preparation={paymentPreparation}
                  actionLoading={actionLoading} onPay={pay} />
              )}
              {view === 'result' && reservation && (
                <ResultView reservation={reservation} onReset={reset} />
              )}
            </section>
            <BookingSummary concert={concert} schedule={schedule} reservation={reservation} />
          </div>
        )}
      </main>

      {view !== 'competition' && competitionSnapshot.runId && (
        <CompetitionOverlay
          snapshot={competitionSnapshot}
          connected={competitionConnected}
          onOpen={() => navigate('competition')}
        />
      )}

      {authOpen && (
        <AuthModal onClose={() => setAuthOpen(false)}
          onAuthenticated={(authenticated) => {
            setUser(authenticated)
            setAuthOpen(false)
            if (schedule && view === 'practice-wait') {
              return
            }
            if (schedule && isScheduleOpen(schedule)) void joinScheduleQueue(schedule)
          }} />
      )}
    </div>
  )
}

function CompetitionOverlay({
  snapshot,
  connected,
  onOpen,
}: {
  snapshot: CompetitionSnapshot
  connected: boolean
  onOpen: () => void
}) {
  const confirmed = snapshot.userStatuses.CONFIRMED ?? 0
  const abandoned = ['ABANDONED_QUEUE', 'ABANDONED_ENTRY', 'ABANDONED_HOLD']
    .reduce((total, status) => total + (snapshot.userStatuses[status] ?? 0), 0)
  const failed = ['FAILED', 'PAYMENT_FAILED']
    .reduce((total, status) => total + (snapshot.userStatuses[status] ?? 0), 0)

  return (
    <button className="competition-overlay" onClick={onOpen}>
      <span className={`competition-live-dot ${connected ? 'connected' : ''}`} />
      <span>
        <strong>가상 사용자 경쟁</strong>
        <small>{snapshot.status} · 완료 {snapshot.completedUsers}/{snapshot.totalUsers}</small>
      </span>
      <span className="competition-overlay-stats">
        <b>성공 {confirmed}</b>
        <b>이탈 {abandoned}</b>
        <b>실패 {failed}</b>
      </span>
    </button>
  )
}

function ConcertList({ concerts, onSelect }: {
  concerts: Concert[]
  onSelect: (concert: Concert) => void
}) {
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Now booking</p>
          <h1>오늘의 무대를<br />가장 먼저 만나세요</h1>
          <p className="muted">공연을 선택하고 예매 흐름을 시작하세요.</p>
        </div>
      </div>
      {concerts.length ? (
        <div className="concert-grid">
          {concerts.map((item, index) => (
            <article className="concert-card" key={item.concertId}>
              <img className="poster" src={item.posterUrl} alt={`${item.title} 포스터`}
                onError={(event) => {
                  event.currentTarget.src = posterFallbacks[index % posterFallbacks.length]
                }} />
              <div className="concert-body">
                <h3>{item.title}</h3>
                <div className="meta-row"><MapPin size={15} /> {item.venue}</div>
                <button className="primary-button" onClick={() => onSelect(item)}>공연 보기</button>
              </div>
            </article>
          ))}
        </div>
      ) : <EmptyState message="등록된 공연이 없습니다." />}
    </>
  )
}

function ScheduleSelection({ schedules, actionLoading, onBack, onSelect }: {
  schedules: Schedule[]
  actionLoading: boolean
  onBack: () => void
  onSelect: (schedule: Schedule) => void
}) {
  return (
    <>
      <button className="ghost-button" onClick={onBack}><ArrowLeft size={17} /> 공연 목록</button>
      <div className="section-heading">
        <p className="eyebrow">Schedule</p>
        <h2>관람할 회차를 선택하세요</h2>
        <p className="muted">예매가 열린 회차만 대기열에 입장할 수 있습니다.</p>
      </div>
      <div className="schedule-list">
        {schedules.map((item) => {
          const effectiveStatus = getEffectiveScheduleStatus(item)
          const open = effectiveStatus === 'BOOKING_OPEN'
          return (
            <button className="schedule-item" disabled={!open || actionLoading}
              key={item.scheduleId} onClick={() => onSelect(item)}>
              <span>
                <strong>{formatDate(item.performanceAt)}</strong>
                <span className="schedule-timing">
                  <span className="schedule-time-row">
                    <span className="schedule-time-label">예매 시작</span>
                    <span>{formatScheduleDeadline(item.bookingOpenAt)}</span>
                  </span>
                  <span className="schedule-time-row deadline">
                    <span className="schedule-time-label"><Clock3 size={14} /> 예매 종료</span>
                    <strong>{formatScheduleDeadline(item.bookingCloseAt)}</strong>
                    <span className="deadline-chip">마감</span>
                  </span>
                </span>
              </span>
              <span className={`status-badge ${open ? '' : 'closed'}`}>
                {statusLabel[effectiveStatus]}
              </span>
            </button>
          )
        })}
      </div>
    </>
  )
}

function PracticeWaitView({
  snapshot,
  schedule,
  countdownMillis,
  loggedIn,
  actionLoading,
  onLogin,
  onJoin,
}: {
  snapshot: CompetitionSnapshot
  schedule: Schedule
  countdownMillis: number
  loggedIn: boolean
  actionLoading: boolean
  onLogin: () => void
  onJoin: () => void
}) {
  const ready = snapshot.userStatuses.READY ?? 0
  const preparing = snapshot.userStatuses.PREPARING ?? 0
  const total = snapshot.totalUsers
  const readyRatio = total ? Math.round((ready / total) * 100) : 0
  const countdownStarted = Boolean(snapshot.startAt)
  const startNow = countdownStarted && countdownMillis <= 0
  const displayedOpenAt = snapshot.startAt ?? schedule.bookingOpenAt

  return (
    <div className="practice-wait-stage">
      <p className="eyebrow">Practice mode</p>
      <h2>
        {startNow
          ? '연습 회차가 열렸습니다'
          : countdownStarted
            ? '연습 티켓팅 시작을 기다리고 있습니다'
            : '가상 사용자를 준비하고 있습니다'}
      </h2>
      <div className="practice-countdown">
        {countdownStarted ? formatCountdown(countdownMillis) : 'READY'}
      </div>
      <p className="muted">
        모든 가상 사용자가 로그인 준비를 마치면 1분 카운트다운 후 대기열이 열립니다.
      </p>

      <div className="practice-schedule-card">
        <span>가상 연습 회차</span>
        <strong>{formatDate(displayedOpenAt)}</strong>
        <small>{startNow ? '입장 가능' : countdownStarted ? '오픈 대기 중' : '가상 사용자 준비 중'}</small>
      </div>

      <div className="practice-ready-panel">
        <div>
          <span>가상 사용자 준비</span>
          <strong>{ready.toLocaleString()} / {total.toLocaleString()}</strong>
        </div>
        <div className="queue-track"><span style={{ width: `${readyRatio}%` }} /></div>
        <small>
          {preparing > 0
            ? `${preparing.toLocaleString()}명이 로그인 준비 중입니다.`
            : '준비된 사용자는 시작 시각까지 대기합니다.'}
        </small>
      </div>

      {!loggedIn ? (
        <button className="primary-button" onClick={onLogin}>
          <UserRound size={17} /> 로그인하고 같이 입장하기
        </button>
      ) : (
        <button className="primary-button" disabled={actionLoading} onClick={onJoin}>
          <Ticket size={17} /> {actionLoading ? '대기열 입장 요청 중입니다' : '연습 회차 입장하기'}
        </button>
      )}
    </div>
  )
}

function QueueView({ queue, actionLoading, onEnter }: {
  queue: QueuePosition
  actionLoading: boolean
  onEnter: () => void
}) {
  const enterable = queue.status === 'ENTERABLE'
  const progress = enterable ? 100
    : Math.max(8, 100 - (queue.position / Math.max(queue.totalWaiting, 1)) * 100)
  return (
    <div className="queue-stage">
      <p className="eyebrow">{enterable ? 'Your turn' : 'Waiting room'}</p>
      <h2>{enterable ? '지금 입장할 수 있습니다' : '접속 순서를 기다리고 있습니다'}</h2>
      <div className="queue-number">{enterable ? 'GO' : queue.position.toLocaleString()}</div>
      <p className="muted">{enterable ? '좌석 선택 화면으로 이동하세요.'
        : `내 앞에 ${Math.max(0, queue.position - 1).toLocaleString()}명이 있습니다.`}</p>
      <div className="queue-track"><span style={{ width: `${progress}%` }} /></div>
      {enterable ? (
        <button className="primary-button" onClick={onEnter} disabled={actionLoading}>
          {actionLoading ? '입장 중...' : '좌석 선택하기'}
        </button>
      ) : <div className="meta-row"><RefreshCw size={15} /> 순번을 자동 갱신 중입니다</div>}
    </div>
  )
}

function SeatSelection({ sections, selectedSection, seats, selectedSeatIds, total,
  actionLoading, onSection, onSeat, onReserve }: {
  sections: SeatSection[]
  selectedSection: SeatSection | null
  seats: Seat[]
  selectedSeatIds: number[]
  total: number
  actionLoading: boolean
  onSection: (section: SeatSection) => void
  onSeat: (seat: Seat) => void
  onReserve: () => void
}) {
  return (
    <>
      <p className="eyebrow">Seat map</p>
      <h2>좌석을 선택하세요</h2>
      <p className="muted">한 번에 최대 4석까지 선택할 수 있습니다.</p>
      <div className="section-list">
        {sections.map((item) => (
          <button className={`section-item ${selectedSection?.sectionId === item.sectionId ? 'selected' : ''}`}
            key={item.sectionId} onClick={() => onSection(item)}>
            <strong>{item.name}석</strong><span>{formatPrice(item.price)}</span>
          </button>
        ))}
      </div>
      <div className="seat-toolbar">
        <strong>{selectedSection?.name ?? '-'} 구역</strong>
        <div className="seat-legend">
          <span><i className="legend-dot available" />선택 가능</span>
          <span><i className="legend-dot selected" />선택</span>
          <span><i className="legend-dot unavailable" />선택 불가</span>
        </div>
      </div>
      <div className="stage">STAGE</div>
      <div className="seat-map">
        {seats.map((seat) => {
          const selected = selectedSeatIds.includes(seat.seatId)
          const unavailable = seat.status !== 'AVAILABLE'
          return (
            <button className={`seat ${selected ? 'selected' : ''} ${unavailable ? 'unavailable' : ''}`}
              disabled={unavailable} key={seat.seatId}
              title={`${seat.rowName}열 ${seat.seatNumber}번`}
              aria-label={`${seat.rowName}열 ${seat.seatNumber}번 좌석`}
              onClick={() => onSeat(seat)} />
          )
        })}
      </div>
      <div className="selection-footer">
        <div><span className="muted">{selectedSeatIds.length}석 선택</span>
          <div className="price">{formatPrice(total)}</div></div>
        <button className="primary-button" disabled={!selectedSeatIds.length || actionLoading}
          onClick={onReserve}>
          {actionLoading ? '좌석 확보 중...' : '좌석 선점하기'}
        </button>
      </div>
    </>
  )
}

function PaymentView({ reservation, hold, preparation, actionLoading, onPay }: {
  reservation: Reservation
  hold: SeatHold | null
  preparation: 'IDLE' | 'PROCESSING' | 'READY' | 'SUCCESS' | 'FAILED'
  actionLoading: boolean
  onPay: (result: 'SUCCESS' | 'FAILED') => void
}) {
  const ready = preparation === 'READY'
  return (
    <>
      <p className="eyebrow">Payment</p><h2>예매 정보를 확인하세요</h2>
      <p className="muted">
        {ready
          ? '결제 준비가 완료되었습니다.'
          : '결제 요청을 준비하고 있습니다. 잠시만 기다려주세요.'}
      </p>
      <div className="reservation-box">
        <InfoRow label="예매 번호" value={String(reservation.reservationId)} />
        <InfoRow label="좌석" value={reservation.seats
          .map((seat) => `${seat.sectionName} ${seat.rowName}${seat.seatNumber}`).join(', ')} />
        <InfoRow label="선점 만료" value={formatDate(hold?.expiresAt)} />
        <InfoRow label="결제 금액" value={formatPrice(reservation.totalAmount)} strong />
      </div>
      <div className="payment-actions">
        <button className="danger-button" disabled={!ready || actionLoading}
          onClick={() => onPay('FAILED')}>결제 실패 Mock</button>
        <button className="primary-button" disabled={!ready || actionLoading}
          onClick={() => onPay('SUCCESS')}>
          {actionLoading
            ? '결제 처리 중...'
            : ready ? '결제 성공 Mock' : '결제 준비 중...'}
        </button>
      </div>
    </>
  )
}

function InfoRow({ label, value, strong = false }: {
  label: string
  value: string
  strong?: boolean
}) {
  return <div className="reservation-row"><span>{label}</span>
    <strong className={strong ? 'price' : ''}>{value}</strong></div>
}

function ResultView({ reservation, onReset }: {
  reservation: Reservation
  onReset: () => void
}) {
  const success = reservation.status === 'CONFIRMED'
  return (
    <div className="result-stage">
      <div className={`result-icon ${success ? '' : 'failed'}`}>
        {success ? <Check size={34} /> : <X size={34} />}
      </div>
      <p className="eyebrow">Reservation result</p>
      <h2>{success ? '예매가 완료되었습니다' : '결제가 완료되지 않았습니다'}</h2>
      <p className="muted">예매 번호 {reservation.reservationId} · {reservation.status}</p>
      <button className="primary-button" onClick={onReset}>다른 공연 보기</button>
    </div>
  )
}

function BookingSummary({ concert, schedule, reservation }: {
  concert: ConcertDetail | null
  schedule: Schedule | null
  reservation: Reservation | null
}) {
  if (!concert) return null
  return (
    <aside className="summary-panel">
      <img className="summary-poster" src={concert.posterUrl} alt=""
        onError={(event) => { event.currentTarget.src = posterFallbacks[0] }} />
      <h3>{concert.title}</h3>
      <div className="meta-row"><MapPin size={14} /> {concert.venue}</div>
      <ul className="summary-list">
        <li><span>관람 일시</span><strong>{formatDate(schedule?.performanceAt)}</strong></li>
        {reservation && <>
          <li><span>선택 좌석</span><strong>{reservation.seats.length}석</strong></li>
          <li><span>결제 금액</span><strong>{formatPrice(reservation.totalAmount)}</strong></li>
        </>}
      </ul>
    </aside>
  )
}

function FlowSteps({ view }: { view: View }) {
  const order: View[] = ['schedule', 'practice-wait', 'queue', 'seats', 'payment', 'result']
  const current = order.indexOf(view)
  return <div className="stepper" aria-label="예매 진행 단계">
    {order.map((step, index) =>
      <span className={`step ${index <= current ? 'active' : ''}`} key={step} />)}
  </div>
}

function AuthModal({ onClose, onAuthenticated }: {
  onClose: () => void
  onAuthenticated: (user: User) => void
}) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setMessage('')
    try {
      if (mode === 'signup') await api.signup({ name, email, password })
      const login = await api.login({ email, password })
      tokenStorage.set(login.accessToken)
      onAuthenticated(await api.me())
    } catch (caught) {
      setMessage(caught instanceof ApiError ? caught.message : '인증에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="auth-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div><p className="eyebrow">Seat Rush account</p>
            <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2></div>
          <button className="icon-button" onClick={onClose} title="닫기"><X size={18} /></button>
        </div>
        <div className="auth-tabs">
          <button className={mode === 'login' ? 'active' : ''}
            onClick={() => setMode('login')}>로그인</button>
          <button className={mode === 'signup' ? 'active' : ''}
            onClick={() => setMode('signup')}>회원가입</button>
        </div>
        <form onSubmit={submit}>
          {mode === 'signup' && <Field id="name" label="이름" value={name}
            onChange={setName} minLength={2} />}
          <Field id="email" label="이메일" value={email} onChange={setEmail} type="email" />
          <Field id="password" label="비밀번호" value={password}
            onChange={setPassword} type="password" minLength={8} />
          {message && <div className="error-banner">{message}</div>}
          <button className="primary-button" disabled={submitting}>
            {submitting ? '처리 중...' : mode === 'login' ? '로그인' : '가입하고 시작하기'}
          </button>
        </form>
      </div>
    </div>
  )
}

function Field({ id, label, value, onChange, type = 'text', minLength }: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  minLength?: number
}) {
  return <div className="form-field"><label htmlFor={id}>{label}</label>
    <input id={id} type={type} value={value} required minLength={minLength}
      onChange={(event) => onChange(event.target.value)} /></div>
}

function LoadingState() {
  return <div className="loading-state"><span className="spinner" />
    <p>정보를 불러오고 있습니다.</p></div>
}

function EmptyState({ message }: { message: string }) {
  return <div className="empty-state"><Ticket size={34} /><p>{message}</p></div>
}
