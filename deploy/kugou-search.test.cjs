'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const search = require('./kugou-search.cjs');

test('song search uses the working public catalog without account credentials', async () => {
  const request = await search({ keywords: '梁博', page: 2, pagesize: 30, cookie: { token: 'private', userid: '123' } }, async value => value);
  assert.equal(request.baseURL + request.url, 'https://songsearch.kugou.com/song_search_v2');
  assert.equal(request.params.keyword, '梁博');
  assert.equal(request.params.page, 2);
  assert.equal(request.params.platform, 'WebFilter');
  assert.equal(request.params.appid, 1014);
  assert.equal(request.params.mid, request.params.clienttime);
  assert.equal(request.params.token, undefined);
  assert.equal(request.params.userid, undefined);
  assert.equal(request.clearDefaultParams, true);
  assert.equal(request.encryptType, 'web');
  assert.deepEqual(request.cookie, {});
});

test('non-song searches keep their upstream routes and platform identity', async () => {
  const cookie = { token: 'private' };
  const request = await search({ keywords: '专辑', type: 'album', cookie }, async value => value);
  assert.equal(request.url, '/v1/search/album');
  assert.equal(request.params.keyword, '专辑');
  assert.equal(request.cookie, cookie);
  assert.equal(request.encryptType, 'android');
});
