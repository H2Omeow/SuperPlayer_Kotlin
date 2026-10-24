import { Buffer } from 'buffer';
import pako from 'pako';
import qs from 'qs';
import { lxCrypto } from './crypto.js';
import { isRecord } from './limits.js';

export function loadLx(script, options, http, bridge) {
  let handler;
  let initialized = false;
  let complete;
  let reject;
  const ready = new Promise((resolve, failure) => { complete = resolve; reject = failure; });
  const eventNames = Object.freeze({ request: 'request', inited: 'inited', updateAlert: 'updateAlert' });
  const metadata = {};
  for (const key of ['name', 'description', 'version', 'author', 'homepage']) {
    metadata[key] = new RegExp(`^\\s*\\*?\\s*@${key}\\s+(.+)$`, 'm').exec(script)?.[1]?.trim() || '';
  }
  const lx = {
    EVENT_NAMES: eventNames, version: '2.0.0', env: options.os === 'android' ? 'mobile' : 'desktop',
    currentScriptInfo: { ...metadata, name: metadata.name || options.name || 'LX', rawScript: script },
    on(event, callback) {
      if (event !== eventNames.request || typeof callback !== 'function') return Promise.reject(new Error('Unsupported LX event'));
      handler = callback;
      return Promise.resolve();
    },
    async send(event, info) {
      if (event === eventNames.updateAlert) return;
      try {
        if (event !== eventNames.inited || initialized) throw new Error('Invalid LX initialization event');
        initialized = true;
        if (!isRecord(info) || info.status === false || !isRecord(info.sources)) throw new Error(info?.message || 'LX initialization failed');
        const sources = {};
        for (const [source, descriptor] of Object.entries(info.sources)) {
          if (!['kw', 'kg', 'tx', 'qq', 'wy', 'mg', 'local'].includes(source) || !isRecord(descriptor) || descriptor.type !== 'music') continue;
          if (!Array.isArray(descriptor.actions) || !Array.isArray(descriptor.qualitys)) throw new Error('Invalid LX source capabilities');
          sources[source] = {
            name: String(descriptor.name || source), type: 'music',
            actions: descriptor.actions.filter((action) => ['musicUrl', 'lyric', 'pic'].includes(action)),
            qualitys: descriptor.qualitys.filter((quality) => ['128k', '192k', '320k', 'flac', 'flac24bit'].includes(quality)),
          };
        }
        if (!Object.keys(sources).length || typeof handler !== 'function') throw new Error('LX must register sources and a request handler');
        complete({
          descriptor: { format: 'lx', name: metadata.name || options.name || 'LX', platform: 'lx',
            version: metadata.version, author: metadata.author, capabilities: ['lxRequest'], sources },
          invoke(method, args) {
            if (method !== 'lxRequest') throw new Error('Unsupported LX method');
            const [source, action, info] = args;
            if (!sources[source]?.actions.includes(action)) throw new Error('LX source does not support this action');
            if (action === 'musicUrl' && !sources[source].qualitys.includes(info?.type)) throw new Error('LX source does not support this quality');
            return handler.call(lx, { source, action, info });
          },
        });
      } catch (failure) { reject(failure); throw failure; }
    },
    request(url, requestOptions = {}, callback) {
      if (typeof callback !== 'function') throw new TypeError('LX request requires a callback');
      if (requestOptions.formData) throw new Error('LX multipart formData is unsupported');
      const controller = new AbortController();
      const headers = { ...requestOptions.headers };
      if (requestOptions.form) headers['Content-Type'] = 'application/x-www-form-urlencoded';
      http.axios(String(url), {
        method: requestOptions.method || 'GET', timeout: requestOptions.timeout, headers,
        data: requestOptions.form ? qs.stringify(requestOptions.form) : requestOptions.body,
        signal: controller.signal, responseType: 'arraybuffer', validateStatus: () => true,
      }).then((response) => {
        const raw = Buffer.from(response.data);
        let body = raw.toString('utf8');
        try { body = JSON.parse(body); } catch {}
        callback(null, { statusCode: response.status, statusMessage: '', headers: response.headers, raw, body }, body);
      }, (failure) => callback(failure, null, null)).catch((failure) => bridge.fail(failure));
      return () => controller.abort();
    },
    utils: {
      crypto: lxCrypto,
      buffer: { from: (...args) => Buffer.from(...args), bufToString: (value, encoding) => Buffer.from(value, 'binary').toString(encoding) },
      zlib: {
        async inflate(value) { return Buffer.from(pako.inflate(value)); },
        async deflate(value) { return Buffer.from(pako.deflate(value)); },
      },
    },
  };
  globalThis.lx = Object.freeze(lx);
  try {
    const result = new Function('lx', script)(lx);
    if (result?.then) result.catch(reject);
  } catch (failure) { reject(failure); }
  return ready;
}
