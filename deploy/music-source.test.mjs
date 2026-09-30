import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac, createHash } from 'node:crypto';
import { createSourceService } from './music-source.mjs';

const hash = text => createHash('sha256').update(text).digest('hex');
test('gateway enforces login, token binding, signature, replay, shared quota and daily reset', async () => {
  let now = Date.UTC(2026, 8, 30, 12);
  let upstreamCalls = 0;
  const service = createSourceService({ masterKey: 'x'.repeat(64), upstream: 'https://example.invalid/api', dailyLimit: 2,
    now: () => now, authenticate: async token => token === 'alice' || token === 'bob' ? { id: token, username: token } : null,
    fetcher: async () => { upstreamCalls++; return { ok: true, text: async () => JSON.stringify({ code: 200, url: 'https://cdn.example/song.mp3', lyric: 'lyrics' }) }; } });
  const keys = {};
  const signed = (token, route, sequence) => {
    const url = '/api/music-source/' + route;
    const timestamp = String(now), nonce = sequence.toString(16).padStart(32, '0');
    const signature = createHmac('sha256', Buffer.from(keys[token].key, 'hex')).update(['GET', url, timestamp, nonce, hash('')].join('\n')).digest('hex');
    return { method: 'GET', url, headers: { authorization: 'Bearer ' + token, 'x-source-key': keys[token].keyId,
      'x-source-time': timestamp, 'x-source-nonce': nonce, 'x-source-signature': signature } };
  };
  try {
    await assert.rejects(service.handle({ method: 'GET', url: '/api/music-source/usage', headers: {} }), { status: 401 });
    for (const token of ['alice', 'bob']) keys[token] = await service.handle({ method: 'POST', url: '/api/music-source/session', headers: { authorization: 'Bearer ' + token } });
    const song = 'music/url?source=wy&musicId=1&quality=320k';
    const first = signed('alice', song, 1);
    assert.equal((await service.handle(first)).code, 200);
    await assert.rejects(service.handle(first), { status: 409 });
    const forged = signed('alice', song, 2); forged.headers['x-source-signature'] = '0'.repeat(64);
    await assert.rejects(service.handle(forged), { status: 403 });
    const stolen = signed('alice', song, 3); stolen.headers.authorization = 'Bearer bob';
    await assert.rejects(service.handle(stolen), { status: 401 });
    await service.handle(signed('bob', song, 4));
    await assert.rejects(service.handle(signed('alice', song, 5)), { status: 429 });
    assert.equal(upstreamCalls, 2);
    await service.handle(signed('alice', 'music/lyric?source=wy&musicId=1', 6));
    const stats = await service.handle(signed('alice', 'usage', 7));
    assert.equal(stats.used, 2); assert.equal(stats.users.length, 2);
    now += 24 * 3600000;
    keys.alice = await service.handle({ method: 'POST', url: '/api/music-source/session', headers: { authorization: 'Bearer alice' } });
    await service.handle(signed('alice', song, 8));
    assert.equal(service.stats({ id: 'alice' }).used, 1);
    await assert.rejects(service.handle(signed('alice', 'https://evil.invalid', 9)), { status: 404 });
  } finally { service.close(); }
});


test('MV gateway requires the same signature and does not consume music quota', async () => {
  let now = Date.UTC(2026, 8, 30, 12);
  const calls = [];
  const service = createSourceService({ masterKey: 'y'.repeat(64), upstream: 'https://example.invalid/api', dailyLimit: 1,
    now: () => now, authenticate: async token => token === 'alice' ? { id: 'alice', username: 'alice' } : null,
    neteaseEndpoint: 'http://netease.local', kugouEndpoint: 'http://kugou.local',
    fetcher: async destination => {
      calls.push(destination.toString());
      if (destination.hostname === 'kugou.local' && destination.pathname === '/search')
        return { ok: true, text: async () => JSON.stringify({ data: { lists: [{ MvHash: 'ABC123', MvName: '测试 MV', SingerName: '歌手', Img: '' }] } }) };
      return { ok: true, text: async () => JSON.stringify({ data: { abc123: { downurl: 'https://cdn.example/mv.mp4' } } }) };
    }
  });
  try {
    const session = await service.handle({ method: 'POST', url: '/api/music-source/session', headers: { authorization: 'Bearer alice' } });
    const signed = (path, nonce) => {
      const timestamp = String(now);
      const signature = createHmac('sha256', Buffer.from(session.key, 'hex'))
        .update(['GET', path, timestamp, nonce, hash('')].join('\n')).digest('hex');
      return { method: 'GET', url: path, headers: { authorization: 'Bearer alice', 'x-source-key': session.keyId,
        'x-source-time': timestamp, 'x-source-nonce': nonce, 'x-source-signature': signature } };
    };
    const search = await service.handle(signed('/api/media/mv/search?source=kugou&keyword=test&page=1', '1'.repeat(32)));
    assert.equal(search.data[0].id, 'ABC123');
    const video = await service.handle(signed('/api/media/mv/url?source=kugou&id=ABC123', '2'.repeat(32)));
    assert.equal(video.url, 'https://cdn.example/mv.mp4');
    assert.equal(service.stats({ id: 'alice' }).used, 0);
    assert.deepEqual(calls, ['http://kugou.local/search?keywords=test&type=mv&page=1&pagesize=30&limit=30&offset=0', 'http://kugou.local/video/url?hash=ABC123']);
  } finally { service.close(); }
});
