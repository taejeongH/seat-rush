import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Gamepad2, LogOut, Ticket, UserRound, X } from 'lucide-react'
import { ApiError, api, tokenStorage } from './api'
import { getBookingApi } from './bookingService'
import { competitionApi, type CompetitionSnapshot } from './competition'
import type { Concert, ConcertDetail, QueuePosition, Reservation, Schedule, Seat, SeatHold, SeatSection, User } from './types'
import './App.css'

// 분리된 모듈형 하위 컴포넌트들을 임포트합니다.
import { CompetitionMode } from './components/CompetitionMode'
import { ConcertList } from './components/ConcertList'
import { ScheduleSelection } from './components/ScheduleSelection'
import { PracticeWaitView } from './components/PracticeWaitView'
import { QueueView } from './components/QueueView'
import { SeatSelection } from './components/SeatSelection'
import { PaymentView } from './components/PaymentView'
import { ResultView } from './components/ResultView'
import { BookingSummary } from './components/BookingSummary'
import { FlowSteps } from './components/FlowSteps'
import { AuthModal } from './components/AuthModal'
import { CompetitionOverlay } from './components/CompetitionOverlay'
import { LoadingState } from './components/Common'

type View =
  | 'concerts'
  | 'competition'
  | 'schedule'
  | 'practice-wait'
  | 'queue'
  | 'seats'
  | 'payment'
  | 'result'

const posterFallbacks = [
  'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=85',
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

const getEffectiveScheduleStatus = (schedule: Schedule): Schedule['status'] => {
  if (schedule.status === 'CANCELED') return 'CANCELED'

  const now = Date.now()
  if (now < new Date(schedule.bookingOpenAt).getTime()) return 'UPCOMING'
  if (now >= new Date(schedule.bookingCloseAt).getTime()) return 'BOOKING_CLOSED'
  return 'BOOKING_OPEN'
}

const isScheduleOpen = (schedule: Schedule) =>
  getEffectiveScheduleStatus(schedule) === 'BOOKING_OPEN'

/**
 * Seat Rush 애플리케이션의 메인 컨테이너 컴포넌트입니다.
 * 전체 예매 상태 제어 및 뷰 전환 제어, 인증 상태 공유를 총괄합니다.
 */
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

  // 연습용 경쟁 모드 상태값
  const [practiceSessionId, setPracticeSessionId] = useState<string | null>(null)
  const [practiceCountdownMillis, setPracticeCountdownMillis] = useState(0)

  // 결제 진행 상태 제어
  const [paymentPreparation, setPaymentPreparation] = useState<
    'IDLE' | 'PROCESSING' | 'READY' | 'SUCCESS' | 'FAILED'
  >('IDLE')

  const paymentInitializationRef = useRef<number | null>(null)
  const autoEnteringScheduleRef = useRef<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)
  const [error, setError] = useState('')

  // 가상 사용자 경쟁 제어기 연동 스냅샷
  const [competitionSnapshot, setCompetitionSnapshot] =
    useState<CompetitionSnapshot>(initialCompetitionSnapshot)
  const [competitionConnected, setCompetitionConnected] = useState(false)
  const practiceStartAt = competitionSnapshot.practiceSessionId === practiceSessionId
    ? competitionSnapshot.startAt
    : null

  // 연습 모드 여부에 따른 예약/결제 API 서비스 매핑
  const bookingApi = useMemo(() => getBookingApi(practiceSessionId), [practiceSessionId])

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
    setError(caught instanceof ApiError ? caught.message : '잠시 후 다시 시도해 주세요.')
  }, [])

  // 브라우저 뒤로가기 처리를 위한 팝스테이트 이벤트 처리
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

  // 초기 공연 리스트 및 사용자 프로필 조회
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

  // 가상 연습 모드 타이머 카운트다운 관리
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

  // 가상 사용자 경쟁 생성기 SSE 실시간 수신 연동
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

  // 대기 순번 및 대기 상태 정기 조회 폴링
  useEffect(() => {
    if (view !== 'queue' || !schedule || queue?.status === 'ENTERABLE') return
    const timer = window.setInterval(async () => {
      try {
        setQueue(await bookingApi.queuePosition(schedule.scheduleId))
      } catch (caught) {
        handleError(caught)
      }
    }, 2000)
    return () => window.clearInterval(timer)
  }, [handleError, view, schedule, queue?.status, bookingApi])

  // 대기 세션 생존 연장을 위한 10초 주기 Heartbeat 전송
  useEffect(() => {
    if (view !== 'queue' || !schedule || queue?.status === 'ENTERABLE') return
    const timer = window.setInterval(() => {
      void bookingApi.queueHeartbeat(schedule.scheduleId).catch(() => undefined)
    }, 10000)
    return () => window.clearInterval(timer)
  }, [view, schedule, queue?.status, bookingApi])

  // PG사 연동 모사 결제 요청 준비 및 상태 조회 폴링
  useEffect(() => {
    if (view !== 'payment' || !reservation) return
    if (paymentInitializationRef.current === reservation.reservationId) return

    paymentInitializationRef.current = reservation.reservationId
    setPaymentPreparation('PROCESSING')

    const prepare = async () => {
      try {
        const requested = await bookingApi.requestPayment(reservation.reservationId)
        setPaymentId(requested.paymentId)

        for (let attempt = 0; attempt < 30; attempt += 1) {
          const preparation = await bookingApi.paymentPreparation(requested.paymentId)
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
  }, [handleError, view, reservation, bookingApi])

  // 특정 공연 상세 선택 및 회차 목록 갱신
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

  // 경쟁 모드(연습) 시작 직후의 흐름 제어 전환
  const continueAfterCompetitionStart = async (
    selectedSchedule: Schedule,
    nextPracticeSessionId: string,
  ) => {
    setLoading(true)
    try {
      setConcert({
        concertId: 0,
        title: '가상 티켓팅 연습',
        venue: '가상 예매 연습 전용관',
        posterUrl: posterFallbacks[0],
        description: '가상 사용자들과 실시간 대기열 진입 및 좌석 선점 경쟁을 연습할 수 있습니다.',
      })
      setSchedules([selectedSchedule])
      setSchedule(selectedSchedule)
      setPracticeSessionId(nextPracticeSessionId)
      setPracticeCountdownMillis(0)
      if (!user) setAuthOpen(true)
      navigate('practice-wait')
    } catch (caught) {
      handleError(caught)
    } finally {
      setLoading(false)
    }
  }

  // 대기열 등록 요청
  const joinScheduleQueue = async (selected: Schedule) => {
    setActionLoading(true)
    setError('')
    try {
      setQueue(await bookingApi.joinQueue(selected.scheduleId))
      navigate('queue')
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  // 연습 모드 대기열 입장 버튼 진입 제어
  const tryJoinPracticeQueue = () => {
    if (!schedule) return
    if (!user) {
      setAuthOpen(true)
      return
    }
    if (!practiceStartAt) {
      window.alert('가상 사용자를 준비하고 있습니다. 잠시만 기다려주세요.')
      return
    }
    if (Date.now() < new Date(practiceStartAt).getTime()) {
      window.alert('아직 예매 시작 시간이 아닙니다.')
      return
    }
    void joinScheduleQueue(schedule)
  }

  // 실제 모드 대기열 진입 제어
  const startQueue = (selected: Schedule) => {
    setSchedule(selected)
    if (!user) {
      setAuthOpen(true)
      return
    }
    if (isScheduleOpen(selected)) void joinScheduleQueue(selected)
  }

  // 대기열 통과 후 좌석 구역 및 구역별 좌석맵 데이터 획득
  const enter = useCallback(async () => {
    if (!schedule) return
    setActionLoading(true)
    try {
      const issued = await bookingApi.enterQueue(schedule.scheduleId)
      const sectionList = await bookingApi.sections(schedule.scheduleId, issued.entryToken)
      setEntryToken(issued.entryToken)
      setSections(sectionList)
      setSection(sectionList[0] ?? null)
      if (sectionList[0]) {
        setSeats(
          await bookingApi.seats(schedule.scheduleId, sectionList[0].sectionId, issued.entryToken)
        )
      }
      navigate('seats', 'replace')
    } catch (caught) {
      autoEnteringScheduleRef.current = null
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }, [handleError, navigate, schedule, bookingApi])

  // 대기열 입장 가능(ENTERABLE) 상태 도달 시 자동 입장 트리거
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

  // 등급/구역 탭 선택에 따른 좌석 갱신
  const changeSection = async (next: SeatSection) => {
    if (!schedule) return
    setActionLoading(true)
    try {
      setSection(next)
      setSelectedSeatIds([])
      setSeats(await bookingApi.seats(schedule.scheduleId, next.sectionId, entryToken))
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  // 좌석 단일 선택/해제 처리
  const toggleSeat = (seat: Seat) => {
    if (seat.status !== 'AVAILABLE') return
    setSelectedSeatIds((current) =>
      current.includes(seat.seatId)
        ? current.filter((id) => id !== seat.seatId)
        : current.length < 4 ? [...current, seat.seatId] : current,
    )
  }

  // 좌석 임시 선점 요청 및 결제 단계 예매 생성
  const holdAndReserve = async () => {
    if (!schedule || !selectedSeatIds.length) return
    setActionLoading(true)
    try {
      const nextHold = await bookingApi.holdSeats(
        schedule.scheduleId, selectedSeatIds, entryToken
      )
      const nextReservation = await bookingApi.createReservation(nextHold.holdId, entryToken)
      setHold(nextHold)
      setReservation(nextReservation)
      navigate('payment')
    } catch (caught) {
      handleError(caught)
    } finally {
      setActionLoading(false)
    }
  }

  // 모킹 결제 승인 요청 완료 및 최종 결과 리포트 대기
  const pay = async (result: 'SUCCESS' | 'FAILED') => {
    if (!reservation || !paymentId || paymentPreparation !== 'READY') return
    setActionLoading(true)
    setError('')
    try {
      await bookingApi.completePayment(paymentId, result)

      let finalReservation = reservation
      for (let attempt = 0; attempt < 20; attempt += 1) {
        finalReservation = await bookingApi.reservation(reservation.reservationId)
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

  // 모든 예매 과정 리셋 및 상태 클리어
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
    setPracticeCountdownMillis(0)
    setPaymentPreparation('IDLE')
    paymentInitializationRef.current = null
    autoEnteringScheduleRef.current = null
    setError('')
  }

  // 사용자 로그아웃 처리
  const logout = () => {
    tokenStorage.clear()
    setUser(null)
    reset()
  }

  // 선택한 좌석의 총합 계산
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

        {loading ? (
          <LoadingState />
        ) : view === 'concerts' ? (
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
                <ScheduleSelection
                  schedules={schedules}
                  actionLoading={actionLoading}
                  onBack={reset}
                  onSelect={startQueue}
                />
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
                <QueueView
                  queue={queue}
                  actionLoading={actionLoading}
                  onEnter={enter}
                />
              )}

              {view === 'seats' && (
                <SeatSelection
                  sections={sections}
                  selectedSection={section}
                  seats={seats}
                  selectedSeatIds={selectedSeatIds}
                  total={selectedTotal}
                  actionLoading={actionLoading}
                  onSection={changeSection}
                  onSeat={toggleSeat}
                  onReserve={holdAndReserve}
                />
              )}

              {view === 'payment' && reservation && (
                <PaymentView
                  reservation={reservation}
                  hold={hold}
                  preparation={paymentPreparation}
                  actionLoading={actionLoading}
                  onPay={pay}
                />
              )}

              {view === 'result' && reservation && (
                <ResultView
                  reservation={reservation}
                  onReset={reset}
                />
              )}
            </section>

            <BookingSummary
              concert={concert}
              schedule={schedule}
              reservation={reservation}
            />
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
        <AuthModal
          onClose={() => setAuthOpen(false)}
          onAuthenticated={(authenticated) => {
            setUser(authenticated)
            setAuthOpen(false)
            if (schedule && view === 'practice-wait') {
              return
            }
            if (schedule && isScheduleOpen(schedule)) void joinScheduleQueue(schedule)
          }}
        />
      )}
    </div>
  )
}
