'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const createRouter = require('./kugou-platform-router.cjs');
const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
const close = server => { server.closeAllConnections(); server.close(); };

test('lite forwards method, form, cookies, timestamp and response cookies', async t => {
  const upstream = http.createServer(async (req, res) => {
    let body = '';
    for await (const chunk of req) body += chunk;
    res.setHeader('Set-Cookie', ['KUGOU_API_PLATFORM=lite; Path=/', 'dfid=test-device; Path=/']);
    res.end(JSON.stringify({ path: req.url, method: req.method, cookie: req.headers.cookie, body }));
  });
  const port = await listen(upstream);
  t.after(() => close(upstream));
  const router = createRouter({ port });
  const gateway = http.createServer((req, res) => router(req, res, () => res.end('standard')));
  const gatewayPort = await listen(gateway);
  t.after(() => close(gateway));
  const response = await fetch('http://127.0.0.1:' + gatewayPort + '/login/cellphone?platform=1&timestamp=123', {
    method: 'POST', body: 'mobile=test&code=000000',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Cookie: 'KUGOU_API_MID=device; dfid=test' },
  });
  assert.equal(response.status, 200);
  assert.equal(response.headers.getSetCookie().length, 2);
  assert.deepEqual(await response.json(), { path: '/login/cellphone?timestamp=123', method: 'POST', cookie: 'KUGOU_API_MID=device; dfid=test', body: 'mobile=test&code=000000' });
  for (const query of ['', '?platform=0', '?platform=ios']) {
    assert.equal(await (await fetch('http://127.0.0.1:' + gatewayPort + '/user/detail' + query)).text(), 'standard');
  }
});

test('unavailable lite backend returns a bounded JSON error', async t => {
  const closed = http.createServer();
  const port = await listen(closed);
  await new Promise(resolve => closed.close(resolve));
  const router = createRouter({ port, timeoutMs: 100 });
  const gateway = http.createServer((req, res) => router(req, res, () => res.end('standard')));
  const gatewayPort = await listen(gateway);
  t.after(() => close(gateway));
  const response = await fetch('http://127.0.0.1:' + gatewayPort + '/login/qr/key?platform=1');
  assert.equal(response.status, 503);
  assert.equal((await response.json()).status, 0);
});
