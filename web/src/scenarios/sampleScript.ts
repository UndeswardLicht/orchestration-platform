/**
 * Starting point for a new scenario (NFR-07: "a sample k6 script button"). The platform passes the
 * run's target URL as `BASE_URL`; the number of VUs and the duration come from the run settings
 * (US-03) and override the script's own options.
 */
export const SAMPLE_SCRIPT = `import http from 'k6/http';
import { check, sleep } from 'k6';

// The run's target URL, e.g. https://demo.example.com
const BASE_URL = __ENV.BASE_URL;

export default function () {
  const res = http.get(\`\${BASE_URL}/\`);
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
`
