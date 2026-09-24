'use strict';
const http = require('node:http');

// Install before body parsers and the API cache in the standard instance only.
module.exports = function createKugouPlatformRouter({ port = 4001, timeoutMs = 25000 } = {}) {
  return function routePlatform(req, res, next) {
    const url = new URL(req.url, 'http://localhost');
    if (url.searchParams.get('platform') !== '1') return next();
    url.searchParams.delete('platform');
    const headers = { ...req.headers, host: '127.0.0.1:' + port };
    delete headers.connection;
    delete headers['proxy-authorization'];
    const upstream = http.request({
      hostname: '127.0.0.1', port, method: req.method,
      path: url.pathname + url.search, headers, timeout: timeoutMs,
    }, incoming => {
      res.writeHead(incoming.statusCode, incoming.headers);
      incoming.on('error', () => res.destroy());
      incoming.pipe(res);
    });
    upstream.on('timeout', () => upstream.destroy(new Error('upstream timeout')));
    upstream.on('error', () => {
      if (res.headersSent) return res.destroy();
      res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify({ status: 0, error_msg: '酷狗概念版服务暂时不可用，请稍后重试' }));
    });
    req.on('aborted', () => upstream.destroy());
    res.on('close', () => { if (!res.writableFinished) upstream.destroy(); });
    req.pipe(upstream);
  };
};
