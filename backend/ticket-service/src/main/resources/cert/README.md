# JWT 인증 키

로컬 개발 환경에서 사용하는 RSA 키 파일을 저장합니다.

- `private-key.pem`: accessToken 서명에 사용하는 PKCS#8 private key
- `public-key.pem`: accessToken 검증에 사용하는 X.509 public key

키 파일은 classpath에서 읽으며 실제 PEM 파일은 Git에 포함하지 않습니다.
