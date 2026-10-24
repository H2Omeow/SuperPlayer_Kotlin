import axiosModule from 'axios';
import { Buffer } from 'buffer';
import { isRecord, limits } from './limits.js';

function decodeResponse(raw, config) {
  if (!isRecord(raw) || !Number.isInteger(raw.status) || raw.status < 100 || raw.status > 599
      || typeof raw.body !== 'string' || !isRecord(raw.headers) || typeof raw.url !== 'string') {
    throw new Error('Invalid host HTTP response');
  }
  if (raw.body.length > Math.ceil(limits.response / 3) * 4) throw new Error('Plugin HTTP response limit exceeded');
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(raw.body)) {
    throw new Error('Invalid host HTTP response base64');
  }
  const bytes = Buffer.from(raw.body, 'base64');
  if (bytes.length > limits.response) throw new Error('Plugin HTTP response limit exceeded');
  const headers = Object.create(null);
  for (const [name, value] of Object.entries(raw.headers)) headers[name.toLowerCase()] = value;
  return {
    data: config.responseType === 'arraybuffer' ? Uint8Array.from(bytes).buffer : bytes.toString('utf8'),
    status: raw.status,
    statusText: '',
    headers,
    config,
    request: { responseURL: raw.url },
  };
}

export function createHttp(bridge) {
  const adapter = async (config) => {
    if (!['json', 'text', 'arraybuffer', undefined, ''].includes(config.responseType)) {
      throw new Error(`Unsupported HTTP responseType: ${config.responseType}`);
    }
    const url = new URL(axiosModule.getUri(config));
    if (!['http:', 'https:'].includes(url.protocol) || url.href.length > 8192) {
      throw new Error('Plugin HTTP URL must use http or https and fit within 8192 characters');
    }
    const method = String(config.method || 'GET').toUpperCase();
    if (!/^[A-Z]{3,12}$/.test(method)) throw new Error('Invalid HTTP method');
    const headers = config.headers.toJSON();
    if (config.auth) {
      headers.Authorization = `Basic ${Buffer.from(`${config.auth.username || ''}:${config.auth.password || ''}`).toString('base64')}`;
    }
    if (Object.keys(headers).length > 128) throw new Error('Too many HTTP headers');
    for (const [name, value] of Object.entries(headers)) {
      if (!/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name) || /[\r\n]/.test(String(value))) {
        throw new Error('Invalid HTTP header');
      }
      headers[name] = String(value);
    }
    const body = config.data == null ? null : config.data;
    if (body !== null && typeof body !== 'string') {
      throw new Error('Binary and multipart HTTP request bodies are unsupported by the host text-body contract');
    }
    if (body && Buffer.byteLength(body) > limits.response) throw new Error('Plugin HTTP body limit exceeded');
    const timeout = Number.isFinite(config.timeout) && config.timeout > 0 ? Math.min(config.timeout, 60000) : limits.timeout;
    const request = bridge.request('http', {
      url: url.href, method, headers, body, responseType: config.responseType || 'json', timeout,
    }, timeout);
    const cancel = () => request.cancel(new axiosModule.CanceledError('Request canceled', config));
    config.signal?.addEventListener('abort', cancel);
    config.cancelToken?.subscribe(cancel);
    try {
      if (config.signal?.aborted || config.cancelToken?.reason) cancel();
      const response = decodeResponse(await request.promise, config);
      if (config.validateStatus && !config.validateStatus(response.status)) {
        throw new axiosModule.AxiosError(`Request failed with status code ${response.status}`,
          response.status >= 500 ? 'ERR_BAD_RESPONSE' : 'ERR_BAD_REQUEST', config, response.request, response);
      }
      return response;
    } catch (error) {
      if (axiosModule.isAxiosError(error)) throw error;
      throw axiosModule.AxiosError.from(error, error.code || 'ERR_NETWORK', config);
    } finally {
      config.signal?.removeEventListener('abort', cancel);
      config.cancelToken?.unsubscribe(cancel);
    }
  };
  const axios = axiosModule.create({ adapter, timeout: limits.timeout });
  for (const name of ['Axios', 'AxiosError', 'AxiosHeaders', 'CanceledError', 'CancelToken', 'isCancel', 'isAxiosError', 'all', 'spread', 'toFormData', 'VERSION']) {
    axios[name] = axiosModule[name];
  }
  axios.default = axios;
  const fetch = async (input, options = {}) => {
    const response = await axios(String(input), {
      method: options.method || 'GET', headers: options.headers, data: options.body,
      signal: options.signal, responseType: 'arraybuffer', validateStatus: () => true,
    });
    const bytes = Buffer.from(response.data);
    const headerEntries = Object.entries(response.headers.toJSON()).map(([name, value]) => [name.toLowerCase(), String(value)]);
    const headers = new Map(headerEntries);
    let consumed = false;
    async function consume(convert) {
      if (consumed) throw new TypeError('Response body has already been consumed');
      consumed = true;
      return convert();
    }
    return {
      ok: response.status >= 200 && response.status < 300,
      status: response.status, statusText: '', url: response.request.responseURL,
      headers: {
        get: (name) => headers.get(String(name).toLowerCase()) ?? null,
        has: (name) => headers.has(String(name).toLowerCase()),
        keys: () => headers.keys(),
        values: () => headers.values(),
        entries: () => headers.entries(),
        forEach: (callback, thisArg) => headers.forEach((value, name) => callback.call(thisArg, value, name)),
        [Symbol.iterator]: () => headers.entries(),
      },
      get bodyUsed() { return consumed; },
      text: () => consume(() => bytes.toString('utf8')),
      json: () => consume(() => JSON.parse(bytes.toString('utf8'))),
      arrayBuffer: () => consume(() => Uint8Array.from(bytes).buffer),
    };
  };
  return { axios, fetch };
}
