import { ArrowLeft, Clock3 } from 'lucide-react'
import type { Schedule } from '../types'

interface ScheduleSelectionProps {
  schedules: Schedule[]
  actionLoading: boolean
  onBack: () => void
  onSelect: (schedule: Schedule) => void
}

const statusLabel: Record<Schedule['status'], string> = {
  UPCOMING: '오픈 예정',
  BOOKING_OPEN: '예매 가능',
  BOOKING_CLOSED: '예매 종료',
  CANCELED: '취소됨',
}

const getEffectiveScheduleStatus = (schedule: Schedule): Schedule['status'] => {
  if (schedule.status === 'CANCELED') return 'CANCELED'

  const now = Date.now()
  if (now < new Date(schedule.bookingOpenAt).getTime()) return 'UPCOMING'
  if (now >= new Date(schedule.bookingCloseAt).getTime()) return 'BOOKING_CLOSED'
  return 'BOOKING_OPEN'
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

/**
 * 선택된 공연의 개별 예매 일정(회차) 목록을 보여주고 선택할 수 있게 하는 컴포넌트입니다.
 */
export function ScheduleSelection({
  schedules,
  actionLoading,
  onBack,
  onSelect
}: ScheduleSelectionProps) {
  return (
    <>
      <button className="ghost-button" onClick={onBack}>
        <ArrowLeft size={17} /> 공연 목록
      </button>
      <div className="section-heading">
        <p className="eyebrow">Schedule</p>
        <h2>관람할 회차를 선택해 주세요</h2>
        <p className="muted">예매 가능 상태의 회차만 대기열 입장이 활성화됩니다.</p>
      </div>
      <div className="schedule-list">
        {schedules.map((item) => {
          const effectiveStatus = getEffectiveScheduleStatus(item)
          const open = effectiveStatus === 'BOOKING_OPEN'
          return (
            <button
              className="schedule-item"
              disabled={!open || actionLoading}
              key={item.scheduleId}
              onClick={() => onSelect(item)}
            >
              <span>
                <strong>{formatDate(item.performanceAt)}</strong>
                <span className="schedule-timing">
                  <span className="schedule-time-row">
                    <span className="schedule-time-label">예매 시작</span>
                    <span>{formatScheduleDeadline(item.bookingOpenAt)}</span>
                  </span>
                  <span className="schedule-time-row deadline">
                    <span className="schedule-time-label">
                      <Clock3 size={14} /> 예매 종료
                    </span>
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
