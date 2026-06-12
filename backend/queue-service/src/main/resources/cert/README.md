# entryToken RSA 키

Queue Service가 entryToken을 서명할 때 사용하는 RSA 키 파일을 저장합니다.

- `entry-token-private-key.pem`: Queue Service에만 배치하는 PKCS#8 private key
- `entry-token-public-key.pem`: 로컬 발급 테스트와 키쌍 검증에 사용하는 X.509 public key

실제 PEM 파일은 Git에 포함하지 않습니다.
