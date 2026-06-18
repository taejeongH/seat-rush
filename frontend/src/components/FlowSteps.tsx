// App.tsx에서 사용하는 View 타입을 동일하게 정의하여 사용합니다.
type View =
  | 'concerts'
  | 'competition'
  | 'schedule'
  | 'practice-wait'
  | 'queue'
  | 'seats'
  | 'payment'
  | 'result'

interface FlowStepsProps {
  view: View
}

/**
 * 예매 절차의 흐름 단계를 상단 인디케이터 바 형태로 시각화해 주는 컴포넌트입니다.
 */
export function FlowSteps({ view }: FlowStepsProps) {
  const order: View[] = ['schedule', 'practice-wait', 'queue', 'seats', 'payment', 'result']
  const current = order.indexOf(view)

  return (
    <div className="stepper" aria-label="예매 진행 단계">
      {order.map((step, index) => (
        <span
          className={`step ${index <= current ? 'active' : ''}`}
          key={step}
        />
      ))}
    </div>
  )
}
