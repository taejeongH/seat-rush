# JWT 검증 키

API Gateway는 Ticket Service가 발급한 accessToken을 검증하기 위해 동일한 RSA public key를 사용합니다.

- `public-key.pem`: accessToken 서명 검증에 사용하는 X.509 public key

실제 PEM 파일은 Git에 포함하지 않습니다.
