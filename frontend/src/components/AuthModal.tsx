import { useState } from 'react'
import type { FormEvent } from 'react'
import { X } from 'lucide-react'
import { ApiError, api, tokenStorage } from '../api'
import type { User } from '../types'
import { Field } from './Common'

type AuthMode = 'login' | 'signup'

interface AuthModalProps {
  onClose: () => void
  onAuthenticated: (user: User) => void
}

/**
 * 로그인과 회원가입을 탭 형식으로 처리할 수 있는 오버레이 모달 팝업 컴포넌트입니다.
 */
export function AuthModal({ onClose, onAuthenticated }: AuthModalProps) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [message, setMessage] = useState('')

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setMessage('')
    try {
      if (mode === 'signup') {
        await api.signup({ name, email, password })
      }
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
          <div>
            <p className="eyebrow">Seat Rush account</p>
            <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
          </div>
          <button className="icon-button" onClick={onClose} title="닫기">
            <X size={18} />
          </button>
        </div>
        <div className="auth-tabs">
          <button
            className={mode === 'login' ? 'active' : ''}
            onClick={() => setMode('login')}
          >
            로그인
          </button>
          <button
            className={mode === 'signup' ? 'active' : ''}
            onClick={() => setMode('signup')}
          >
            회원가입
          </button>
        </div>
        <form onSubmit={handleSubmit}>
          {mode === 'signup' && (
            <Field
              id="name"
              label="이름"
              value={name}
              onChange={setName}
              minLength={2}
            />
          )}
          <Field
            id="email"
            label="이메일"
            value={email}
            onChange={setEmail}
            type="email"
          />
          <Field
            id="password"
            label="비밀번호"
            value={password}
            onChange={setPassword}
            type="password"
            minLength={8}
          />

          {message && (
            <div className="error-banner" style={{ marginTop: '12px', marginBottom: '12px' }}>
              {message}
            </div>
          )}

          <button className="primary-button" disabled={submitting}>
            {submitting ? '처리 중...' : mode === 'login' ? '로그인' : '가입하고 시작하기'}
          </button>
        </form>
      </div>
    </div>
  )
}
