import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import vm from 'node:vm';

const bundle = await readFile(new URL('../../../assets/music_plugins/runtime.js', import.meta.url), 'utf8');

function runtime() {
  const context = vm.createContext({ __nekoRandomHex: (length) => randomBytes(length).toString('hex') });
  vm.runInContext(bundle, context, { timeout: 1000 });
  return context.NekoPlugin;
}

async function settle(host, respond) {
  for (let attempt = 0; attempt < 30; attempt++) {
    await new Promise((resolve) => setImmediate(resolve));
    const state = JSON.parse(host.take());
    if (state.state !== 'pending') return state;
    for (const request of state.requests) {
      assert.ok(respond, JSON.stringify(request));
      host.deliver(request.id, true, await respond(request));
    }
  }
  assert.fail('Plugin did not settle');
}

test('asynchronous LX initialization and unsupported quality', async () => {
  const host = runtime();
  host.load(`
    lx.on(lx.EVENT_NAMES.request,async()=> 'https://example.test/audio');
    setTimeout(()=>lx.send(lx.EVENT_NAMES.inited,{sources:{wy:{type:'music',actions:['musicUrl'],qualitys:['320k']}}}),1);
  `, { format: 'lx' });
  assert.equal((await settle(host, (request) => { assert.equal(request.kind, 'delay'); return null; })).state, 'done');
  host.invoke('lxRequest', ['wy', 'musicUrl', { type: 'flac', musicInfo: {} }]);
  assert.match((await settle(host)).error, /quality/);
});

test('axios preserves response status errors', async () => {
  const host = runtime();
  host.load(`module.exports={platform:'fixture',search:async()=>require('axios').get('https://example.test')}`, {});
  assert.equal((await settle(host)).state, 'done');
  host.invoke('search', []);
  const result = await settle(host, () => ({ status: 403, headers: {}, url: 'https://example.test', body: '' }));
  assert.match(result.error, /403/);
});

test('cancelled HTTP calls release host requests', async () => {
  const host = runtime();
  host.load(`module.exports={platform:'fixture',search:async()=>{
    const controller=new AbortController();controller.abort();
    try{await require('axios').get('https://example.test',{signal:controller.signal})}
    catch(error){return require('axios').isCancel(error)}
  }}`, {});
  await settle(host);
  host.invoke('search', []);
  const result = await settle(host);
  assert.equal(result.value, true);
  assert.deepEqual(result.requests, []);
});

test('ordinary JavaScript META does not affect CommonJS execution', async () => {
  const host = runtime();
  host.load(`const META={name:'other'};module.exports={platform:'fixture',search:()=>META.name}`, {});
  assert.equal((await settle(host)).value.platform, 'fixture');
  host.invoke('search', []);
  assert.equal((await settle(host)).value, 'other');
});

test('WebDAV uses bounded host HTTP and preserves detailed responses', async () => {
  const host = runtime();
  host.load(`module.exports={platform:'fixture',search:async()=>{
    const client=require('webdav').createClient('https://example.test/dav',{username:'user',password:'pass'});
    return client.getFileContents('/song.lrc',{format:'text',details:true});
  }}`, {});
  assert.equal((await settle(host)).state, 'done');
  host.invoke('search', []);
  const result = await settle(host, (request) => {
    assert.equal(request.kind, 'http');
    assert.equal(request.payload.url, 'https://example.test/dav/song.lrc');
    assert.equal(request.payload.headers.Authorization, 'Basic dXNlcjpwYXNz');
    return { status: 200, headers: { 'content-type': 'text/plain' }, url: request.payload.url,
      body: Buffer.from('[00:01]歌词').toString('base64') };
  });
  assert.equal(result.state, 'done', result.error);
  assert.equal(result.value.data, '[00:01]歌词');
  assert.equal(result.value.headers['content-type'], 'text/plain');
});

test('deprecated native cookies cannot expose account cookies', async () => {
  const host = runtime();
  host.load(`module.exports={platform:'fixture',search:()=>require('@react-native-cookies/cookies').get('https://example.test')}`, {});
  await settle(host);
  host.invoke('search', []);
  assert.match((await settle(host)).error, /Native cookie access is unsupported/);
});
