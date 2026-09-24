// Adapted from MakcRe/KuGouMusicApi b624d645 (MIT; see app/src/main/assets/licenses/KuGouMusicApi-MIT.txt).
// 搜索
// type=song: /v2/search/song，type=album: /v1/search/album，其余类型: /v1/search/{type}
module.exports = (params, useAxios) => {
  const type = ['special', 'lyric', 'song', 'album', 'author', 'mv'].includes(params.type) ? params.type : 'song';
  const isLite = process.env.platform === 'lite';
  const keyword = params?.keywords || params?.keyword || '';

  if (type === 'song') {
    // Public catalog only. Account rights remain checked by privilege/song URL endpoints.
    // Android /v2 and /v3 currently return business error 152, including upstream requests.
    const now = Date.now();
    return useAxios({
      baseURL: 'https://songsearch.kugou.com',
      url: '/song_search_v2',
      method: 'GET',
      params: { keyword, page: params?.page || 1, pagesize: params?.pagesize || 30, platform: 'WebFilter',
        appid: 1014, srcappid: 2919, clientver: 20000, clienttime: now, mid: now, uuid: now, dfid: '-' },
      headers: { 'User-Agent': 'Mozilla/5.0', Referer: 'https://www.kugou.com/' },
      clearDefaultParams: true,
      encryptType: 'web',
      cookie: {},
    });
  }

  if (type === 'album') {
    // sorttype: 0=全部, 1=最新
    const dataMap = {
      keyword,
      page: params?.page || 1,
      pagesize: params?.pagesize || 20,
      platform: 'AndroidFilter',
      iscorrection: params?.iscorrection ?? 1,
      category: params?.category || '1',
      sorttype: Number(params?.sorttype) === 1 ? 1 : 0,
      searchsong: Number(params?.searchsong) === 1 ? 1 : 0,
    };

    // 传 tag=em 时返回带 <em> 高亮的关键词
    if (params?.tag) dataMap.tag = params.tag;

    if (isLite) dataMap.clientver = 201;

    return useAxios({
      url: '/v1/search/album',
      method: 'GET',
      params: dataMap,
      encryptType: 'android',
      headers: { 'x-router': 'complexsearch.kugou.com' },
      cookie: params?.cookie || {},
    });
  }

  const dataMap = {
    // token: '',
    albumhide: 0,
    iscorrection: 1,
    keyword,
    nocollect: 0,
    page: params?.page || 1,
    pagesize: params?.pagesize || 30,
    platform: 'AndroidFilter',
  };

  return useAxios({
    url: `/v1/search/${type}`,
    method: 'GET',
    params: dataMap,
    encryptType: 'android',
    headers: { 'x-router': 'complexsearch.kugou.com' },
    cookie: params?.cookie || {},
  });
};
