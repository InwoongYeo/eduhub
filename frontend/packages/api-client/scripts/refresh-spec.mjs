// 실행 중인 백엔드에서 OpenAPI 문서를 받아 openapi.json을 갱신한다.
// 사용: npm run refresh:spec -w packages/api-client  (기본 http://localhost:8080, API_DOCS_URL로 변경 가능)
import { writeFile } from 'node:fs/promises';

const url = process.env.API_DOCS_URL ?? 'http://localhost:8080/v3/api-docs';
const response = await fetch(url);
if (!response.ok) {
  throw new Error(`OpenAPI 문서를 받지 못했습니다: ${response.status} ${url}`);
}
const spec = await response.json();
// 생성 클라이언트는 같은 출처(개발 서버 프록시)로 호출한다
spec.servers = [{ url: '', description: '개발 서버 프록시(/api → localhost:8080)' }];
await writeFile(new URL('../openapi.json', import.meta.url), `${JSON.stringify(spec, null, 2)}\n`);
console.log(`openapi.json 갱신: ${url}`);
