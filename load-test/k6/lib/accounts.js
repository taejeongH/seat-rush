import http from 'k6/http';
import { fail } from 'k6';

const accountPoolFile = __ENV.ACCOUNT_POOL_FILE
  || '../../virtual-user-generator/data/virtual-user-accounts.json';
const accounts = JSON.parse(open(accountPoolFile));

/**
 * 측정 시작 전에 테스트 계정의 accessToken을 준비합니다.
 */
export function prepareAccessTokens(targetUsers, concurrency) {
  if (accounts.length < targetUsers) {
    fail(`test account pool is insufficient: required=${targetUsers}, available=${accounts.length}`);
  }

  const selectedAccounts = accounts.slice(0, targetUsers);
  const accessTokens = [];

  for (let offset = 0; offset < selectedAccounts.length; offset += concurrency) {
    const batch = selectedAccounts.slice(offset, offset + concurrency);
    const responses = http.batch(batch.map((account) => ({
      method: 'POST',
      url: `${__ENV.BASE_URL || 'http://localhost:8080'}/api/auth/login`,
      body: JSON.stringify({ email: account.email, password: account.password }),
      params: {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'auth.preparation' },
      },
    })));

    responses.forEach((response, index) => {
      let body;
      try {
        body = response.json();
      } catch (error) {
        fail(`account preparation returned non-json response: status=${response.status}`);
      }

      const accessToken = body?.result?.accessToken;
      if (response.status < 200 || response.status >= 300 || !body?.isSuccess || !accessToken) {
        fail(`account preparation failed: email=${batch[index].email}, status=${response.status}, code=${body?.code}`);
      }
      accessTokens.push(accessToken);
    });
  }

  return accessTokens;
}
