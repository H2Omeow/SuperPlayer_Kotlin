import { createServer } from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { pathToFileURL } from 'node:url';

const PREFIX = '/api/music-source/';
const platforms = new Set(['kg', 'kw', 'wy', 'tx', 'mg', 'bilibili']);
const digest = value => createHash('sha256').update(value).digest('hex');
const fail = (status, message) => Object.assign(new Error(message), { status });
const dayAt = now => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date(now));
const safeEqual = (left, right) => {
  const first = Buffer.from(left), second = Buffer.from(right);
  return first.length === second.length && timingSafeEqual(first, second);
};

export function createSourceService({ database = ':memory:', masterKey, upstream, upstreamKey = '',
  authenticate, allUsers = () => [], now = Date.now, dailyLimit = 10000, fetcher = fetch,
  neteaseEndpoint = 'http://127.0.0.1:3000', kugouEndpoint = 'http://127.0.0.1:4000', dashboard }) {
  if (!masterKey || masterKey.length < 32 || !authenticate) throw new Error('Missing secure configuration');
  const db = new DatabaseSync(database);
  db.exec(`PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, uid TEXT, token_hash TEXT, expires INTEGER);
    CREATE TABLE IF NOT EXISTS nonces (session TEXT, nonce TEXT, expires INTEGER, PRIMARY KEY(session, nonce));
    CREATE TABLE IF NOT EXISTS daily (day TEXT PRIMARY KEY, count INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS calls (uid TEXT, day TEXT, name TEXT, count INTEGER, PRIMARY KEY(uid, day));
    CREATE INDEX IF NOT EXISTS nonce_expiry ON nonces(expires);`);
  const derive = session => createHmac('sha256', masterKey).update(session.id + ':' + session.token_hash).digest('hex');
  const sessionRate = new Map();
  const callRate = new Map();
  function throttle(map, id, max) {
    const minute = Math.floor(now() / 60000);
    const entry = map.get(id);
    const next = entry?.minute === minute ? { minute, count: entry.count + 1 } : { minute, count: 1 };
    map.set(id, next);
    if (map.size > 10000) for (const [key, value] of map) if (value.minute < minute) map.delete(key);
    if (next.count > max) throw fail(429, '请求过于频繁，请稍后重试');
  }
  function stats(user) {
    const day = dayAt(now());
    const used = db.prepare('SELECT count FROM daily WHERE day=?').get(day)?.count || 0;
    const counts = db.prepare('SELECT uid, MAX(name) AS name, SUM(count) AS total, SUM(CASE WHEN day=? THEN count ELSE 0 END) AS today FROM calls GROUP BY uid').all(day);
    const known = new Map(allUsers().map(account => [String(account.id), { name: account.nickname || account.username || '本站用户', today: 0, total: 0 }]));
    for (const count of counts) known.set(count.uid, { name: known.get(count.uid)?.name || count.name, today: count.today, total: count.total });
    return { code: 200, date: day, limit: dailyLimit, used, remaining: Math.max(0, dailyLimit - used),
      myToday: counts.find(count => count.uid === String(user.id))?.today || 0,
      users: [...known.values()].sort((left, right) => right.today - left.today || right.total - left.total) };
  }
  function consume(user) {
    const day = dayAt(now());
    db.exec('BEGIN IMMEDIATE');
    try {
      db.prepare('INSERT OR IGNORE INTO daily VALUES (?, 0)').run(day);
      const changed = db.prepare('UPDATE daily SET count=count+1 WHERE day=? AND count<?').run(day, dailyLimit);
      if (!changed.changes) throw fail(429, '本站音源今日共享额度已用尽，次日北京时间零点恢复');
      db.prepare('INSERT INTO calls VALUES (?, ?, ?, 1) ON CONFLICT(uid,day) DO UPDATE SET count=count+1,name=excluded.name')
        .run(String(user.id), day, user.nickname || user.username || '本站用户');
      db.exec('COMMIT');
    } catch (error) { db.exec('ROLLBACK'); throw error; }
  }
  async function handle(request) {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname === PREFIX + 'health') return { code: 200, status: 'ok' };
    if (url.pathname.startsWith('/api/media/')) {
      const platform = url.searchParams.get('source');
      if (request.method !== 'GET' || !['netease', 'kugou'].includes(platform)) throw fail(400, '不支持的 MV 平台');
      const token = request.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
      if (!token || token.length > 8192) throw fail(401, '请先登录本站账号');
      const user = await authenticate(token);
      if (!user?.id) throw fail(401, '本站登录已失效，请重新登录');
      const keyId = request.headers['x-source-key'];
      const session = db.prepare('SELECT * FROM sessions WHERE id=?').get(keyId || '');
      if (!session || session.expires <= now() || session.uid !== String(user.id) || session.token_hash !== digest(token)) throw fail(401, '音源密钥已失效，请重试');
      const timestamp = request.headers['x-source-time'] || '';
      const nonce = request.headers['x-source-nonce'] || '';
      const signature = request.headers['x-source-signature'] || '';
      if (!/^\d{13}$/.test(timestamp) || Math.abs(now() - Number(timestamp)) > 60000 || !/^[a-f0-9]{32}$/.test(nonce)) throw fail(403, '请求已过期或签名参数无效');
      const canonical = [request.method, url.pathname + url.search, timestamp, nonce, digest('')].join('\n');
      const expected = createHmac('sha256', Buffer.from(derive(session), 'hex')).update(canonical).digest('hex');
      if (!safeEqual(signature, expected)) throw fail(403, '请求签名校验失败');
      db.prepare('DELETE FROM nonces WHERE expires<?').run(now());
      try { db.prepare('INSERT INTO nonces VALUES (?, ?, ?)').run(keyId, nonce, now() + 120000); }
      catch { throw fail(409, '请求已使用，请勿重复提交'); }
      throttle(callRate, 'media:' + (request.socket?.remoteAddress || 'local'), 120);
      const call = async (endpoint, parameters) => {
        const destination = new URL((platform === 'kugou' ? kugouEndpoint : neteaseEndpoint) + endpoint);
        for (const [key, value] of Object.entries(parameters)) destination.searchParams.set(key, String(value));
        const response = await fetcher(destination, { signal: AbortSignal.timeout(20000), redirect: 'error' });
        if (!response.ok) throw fail(502, 'MV 服务暂时不可用');
        return JSON.parse(await response.text());
      };
      if (url.pathname === '/api/media/mv/search') {
        const keyword = url.searchParams.get('keyword')?.trim();
        if (!keyword || keyword.length > 256) throw fail(400, '请输入 MV 名称');
        const page = Math.min(100, Math.max(1, Number(url.searchParams.get('page')) || 1));
        const response = await call('/search', { keywords: keyword, type: platform === 'kugou' ? 'mv' : '1004', page, pagesize: 30, limit: 30, offset: (page - 1) * 30 });
        const items = platform === 'kugou' ? response.data?.lists || [] : response.result?.mvs || [];
        return { code: 200, data: items.map(item => ({ id: String(item.MvHash || item.id || ''), title: item.MvName || item.name || '', artist: item.SingerName || item.artistName || '', artwork: item.cover || null })) };
      }
      if (url.pathname === '/api/media/mv/url') {
        const id = url.searchParams.get('id');
        if (!id || !/^[a-zA-Z0-9]{1,128}$/.test(id)) throw fail(400, 'MV 编号无效');
        const response = await call(platform === 'kugou' ? '/video/url' : '/mv/url', platform === 'kugou' ? { hash: id } : { id, r: 1080 });
        const item = platform === 'kugou' ? response.data?.[id.toLowerCase()] : response.data;
        const value = item?.url || item?.downurl || response.url || item?.play_url;
        const link = Array.isArray(value) ? value.find(item => typeof item === 'string' && /^https?:\/\//.test(item)) : value;
        if (typeof link !== 'string' || !/^https?:\/\//.test(link)) throw fail(404, '暂无可播放 MV');
        return { code: 200, url: link };
      }
      throw fail(404, '接口不存在');
    }
    const token = request.headers.authorization?.match(/^Bearer (\S+)$/)?.[1];
    if (!token || token.length > 8192) throw fail(401, '请先登录本站账号');
    throttle(sessionRate, 'auth:' + (request.socket?.remoteAddress || 'local'), 3000);
    const user = await authenticate(token);
    if (!user?.id) throw fail(401, '本站登录已失效，请重新登录');
    if (request.method === 'POST' && url.pathname === PREFIX + 'session') {
      throttle(sessionRate, String(user.id), 10);
      const tokenHash = digest(token);
      const expires = now() + 3600000;
      const session = { id: randomBytes(16).toString('hex'), token_hash: tokenHash };
      db.prepare('DELETE FROM sessions WHERE expires<?').run(now());
      db.prepare('INSERT INTO sessions VALUES (?, ?, ?, ?)').run(session.id, String(user.id), tokenHash, expires);
      return { code: 200, keyId: session.id, key: derive(session), expires };
    }
    if (request.method !== 'GET' || !url.pathname.startsWith(PREFIX)) throw fail(404, '接口不存在');
    const keyId = request.headers['x-source-key'];
    const session = db.prepare('SELECT * FROM sessions WHERE id=?').get(keyId || '');
    if (!session || session.expires <= now() || session.uid !== String(user.id) || session.token_hash !== digest(token)) throw fail(401, '音源密钥已失效，请重试');
    const timestamp = request.headers['x-source-time'] || '';
    const nonce = request.headers['x-source-nonce'] || '';
    const signature = request.headers['x-source-signature'] || '';
    if (!/^\d{13}$/.test(timestamp) || Math.abs(now() - Number(timestamp)) > 60000 || !/^[a-f0-9]{32}$/.test(nonce)) throw fail(403, '请求已过期或签名参数无效');
    const canonical = [request.method, url.pathname + url.search, timestamp, nonce, digest('')].join('\n');
    const expected = createHmac('sha256', Buffer.from(derive(session), 'hex')).update(canonical).digest('hex');
    if (!safeEqual(signature, expected)) throw fail(403, '请求签名校验失败');
    db.prepare('DELETE FROM nonces WHERE expires<?').run(now());
    try { db.prepare('INSERT INTO nonces VALUES (?, ?, ?)').run(keyId, nonce, now() + 120000); }
    catch { throw fail(409, '请求已使用，请勿重复提交'); }
    throttle(callRate, String(user.id), 120);
    if (url.pathname === PREFIX + 'usage') return stats(user);
    const route = url.pathname.slice(PREFIX.length);
    const allowed = new Set(['music/search', 'music/url', 'music/lyric', 'music/mv/search', 'music/mv/url']);
    if (!allowed.has(route)) throw fail(404, '接口不存在');
    const platform = url.searchParams.get('platform') || url.searchParams.get('source');
    if (!platforms.has(platform)) throw fail(400, '不支持的搜源');
    const destination = new URL(upstream.replace(/\/$/, '') + '/' + route);
    const permitted = ['platform', 'source', 'keyword', 'page', 'limit', 'musicId', 'id', 'quality'];
    for (const key of permitted) {
      const value = url.searchParams.get(key);
      if (value !== null) {
        if (value.length > 256) throw fail(400, '参数过长');
        destination.searchParams.set(key, value);
      }
    }
    if (route.endsWith('search')) {
      const keyword = url.searchParams.get('keyword')?.trim();
      if (!keyword) throw fail(400, '请输入搜索关键词');
      destination.searchParams.set('page', String(Math.min(100, Math.max(1, Number(url.searchParams.get('page')) || 1))));
      destination.searchParams.set('limit', String(Math.min(30, Math.max(1, Number(url.searchParams.get('limit')) || 30))));
    } else if (!(url.searchParams.get('musicId') || url.searchParams.get('id'))) throw fail(400, '缺少歌曲或 MV 编号');
    if (route === 'music/url') {
      if (!new Set(['128k', '320k', 'flac', 'hires']).has(url.searchParams.get('quality'))) throw fail(400, '不支持的音质');
      consume(user);
    }
    if (upstreamKey) destination.searchParams.set('key', upstreamKey);
    let body;
    try {
      const response = await fetcher(destination, { signal: AbortSignal.timeout(20000), redirect: 'error',
        headers: upstreamKey ? { 'X-Api-Key': upstreamKey, Accept: 'application/json' } : { Accept: 'application/json' } });
      if (!response.ok) throw new Error('upstream unavailable');
      const text = await response.text();
      if (text.length > 2 * 1024 * 1024) throw new Error('response too large');
      body = JSON.parse(text);
    } catch { throw fail(502, '音源服务暂时不可用，请稍后重试'); }
    if (Number(body.code) !== 200) throw fail(502, '音源未返回可用结果');
    if (route === 'music/url' || route === 'music/mv/url') {
      const link = body.url || body.data?.url;
      if (typeof link !== 'string' || !/^https?:\/\//.test(link)) throw fail(404, '暂无可播放链接');
      return { code: 200, url: link };
    }
    if (route === 'music/lyric') return { code: 200, lyric: body.lyric || '', tlyric: body.tlyric || '', format: body.format || 'lrc' };
    return { code: 200, isEnd: body.isEnd !== false, data: (body.data || []).map(item => ({
      id: String(item.id || item.songId || ''), songId: String(item.songId || ''), title: String(item.title || item.name || ''),
      artist: String(item.artist || ''), album: String(item.album || ''), duration: Number(item.duration) || 0,
      artwork: typeof item.artwork === 'string' && /^https?:\/\//.test(item.artwork) && new URL(item.artwork).hostname !== new URL(upstream).hostname ? item.artwork : null
    })) };
  }
  const server = createServer(async (request, response) => {
    if (request.url === '/source/' && dashboard) {
      response.setHeader('Content-Type', 'text/html; charset=utf-8');
      response.setHeader('Cache-Control', 'no-store');
      response.setHeader('Referrer-Policy', 'no-referrer');
      response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
      response.end(readFileSync(dashboard));
      return;
    }
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    try { response.end(JSON.stringify(await handle(request))); }
    catch (error) { response.statusCode = error.status || 500; response.end(JSON.stringify({ code: response.statusCode, message: error.status ? error.message : '服务器内部错误' })); }
  });
  server.requestTimeout = 25000;
  server.headersTimeout = 10000;
  return { server, handle, stats, close: () => { server.close(); db.close(); } };
}

export function startSourceServer() {
  process.umask(0o077);
  const config = JSON.parse(readFileSync(process.env.SOURCE_CONFIG || './private/config.json', 'utf8'));
  mkdirSync(dirname(config.database), { recursive: true, mode: 0o700 });
  const service = createSourceService({ ...config,
    allUsers: () => JSON.parse(readFileSync(config.accountsFile, 'utf8')).map(user => ({ id: user.id, username: user.username, nickname: user.nickname })),
    authenticate: async token => {
      try {
        const response = await fetch(config.accountEndpoint, { headers: { Authorization: 'Bearer ' + token }, signal: AbortSignal.timeout(5000), redirect: 'error' });
        if (!response.ok) return null;
        const body = await response.json();
        return body.code === 0 ? body.user : null;
      } catch { throw fail(503, '账号服务暂时不可用'); }
    }
  });
  service.server.listen(config.port, '127.0.0.1', () => console.log('Music source gateway ready on loopback'));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { service.server.close(() => { service.close(); process.exit(0); }); });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) startSourceServer();
