import { Ticket } from 'lucide-react'

interface FieldProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  minLength?: number
}

/**
 * 모달이나 폼 내에서 일관되게 사용되는 텍스트 입력 필드 컴포넌트입니다.
 */
export function Field({ id, label, value, onChange, type = 'text', minLength }: FieldProps) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        required
        minLength={minLength}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  )
}

/**
 * 데이터를 불러오는 동안 표시되는 일관된 형태의 로딩 스피너 컴포넌트입니다.
 */
export function LoadingState() {
  return (
    <div className="loading-state">
      <span className="spinner" />
      <p>정보를 불러오고 있습니다. 잠시만 기다려주세요.</p>
    </div>
  )
}

/**
 * 렌더링할 데이터 리스트가 없을 때 빈 화면을 채워주는 컴포넌트입니다.
 */
export function EmptyState({ message }: { message: string }) {
  return (
    <div className="empty-state">
      <Ticket size={34} />
      <p>{message}</p>
    </div>
  )
}
