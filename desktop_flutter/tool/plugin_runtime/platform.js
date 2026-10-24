import { Buffer } from 'buffer';

const hostRandomHex = globalThis.__nekoRandomHex;

export function randomBytes(length) {
  if (!Number.isInteger(length) || length < 0 || length > 65536) {
    throw new RangeError('Random byte length must be an integer between 0 and 65536');
  }
  if (typeof hostRandomHex !== 'function') throw new Error('Secure random host hook __nekoRandomHex is unavailable');
  const hex = hostRandomHex(length);
  if (typeof hex !== 'string' || hex.length !== length * 2 || !/^[\da-f]*$/i.test(hex)) {
    throw new Error('Secure random host hook returned invalid hex');
  }
  return Buffer.from(hex, 'hex');
}

globalThis.Buffer = Buffer;
globalThis.global = globalThis;
globalThis.window = globalThis;
globalThis.self = globalThis;
globalThis.fetch = async () => { throw new Error('Plugin HTTP bridge is not initialized'); };
globalThis.crypto = Object.freeze({
  getRandomValues(array) {
    if (!ArrayBuffer.isView(array) || !/^(?:Uint(?:8|16|32)|Int(?:8|16|32)|Uint8Clamped|BigInt64|BigUint64)Array$/.test(array.constructor.name)) {
      throw new TypeError('getRandomValues requires an integer typed array');
    }
    if (array.byteLength > 65536) throw new RangeError('getRandomValues exceeds 65536 bytes');
    new Uint8Array(array.buffer, array.byteOffset, array.byteLength).set(randomBytes(array.byteLength));
    return array;
  },
});

globalThis.TextEncoder = class TextEncoder {
  get encoding() { return 'utf-8'; }
  encode(value = '') { return Uint8Array.from(Buffer.from(String(value), 'utf8')); }
  encodeInto(value, destination) {
    if (!(destination instanceof Uint8Array)) throw new TypeError('encodeInto requires Uint8Array');
    let read = 0;
    let written = 0;
    for (const character of String(value)) {
      const bytes = this.encode(character);
      if (written + bytes.length > destination.length) break;
      destination.set(bytes, written);
      written += bytes.length;
      read += character.length;
    }
    return { read, written };
  }
};

globalThis.TextDecoder = class TextDecoder {
  constructor(label = 'utf-8', options = {}) {
    const normalized = String(label).trim().toLowerCase();
    if (!['utf-8', 'utf8', 'unicode-1-1-utf-8'].includes(normalized)) {
      throw new RangeError(`Unsupported text encoding: ${label}`);
    }
    this.encoding = 'utf-8';
    this.fatal = Boolean(options.fatal);
    this.ignoreBOM = Boolean(options.ignoreBOM);
  }
  decode(value = new Uint8Array(), options = {}) {
    if (options.stream) throw new Error('Streaming TextDecoder is unsupported');
    const bytes = ArrayBuffer.isView(value)
      ? Buffer.from(value.buffer, value.byteOffset, value.byteLength) : Buffer.from(value);
    const text = bytes.toString('utf8');
    if (this.fatal && !Buffer.from(text).equals(bytes)) throw new TypeError('Invalid UTF-8 data');
    return !this.ignoreBOM && text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  }
};

globalThis.atob = (value) => {
  const text = String(value).replace(/[\t\n\f\r ]/g, '');
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=?)?$/.test(text)) {
    throw new TypeError('Invalid base64 data');
  }
  return Buffer.from(text, 'base64').toString('latin1');
};
globalThis.btoa = (value) => {
  const text = String(value);
  if (/[^\x00-\xff]/.test(text)) throw new TypeError('btoa requires a binary string');
  return Buffer.from(text, 'latin1').toString('base64');
};

globalThis.AbortSignal = class AbortSignal {
  aborted = false;
  reason = undefined;
  listeners = new Set();
  addEventListener(event, callback) { if (event === 'abort') this.listeners.add(callback); }
  removeEventListener(event, callback) { if (event === 'abort') this.listeners.delete(callback); }
  throwIfAborted() { if (this.aborted) throw this.reason; }
};
globalThis.AbortController = class AbortController {
  signal = new globalThis.AbortSignal();
  abort(reason = new Error('The operation was aborted')) {
    if (this.signal.aborted) return;
    this.signal.aborted = true;
    this.signal.reason = reason;
    const event = { type: 'abort', target: this.signal };
    for (const callback of this.signal.listeners) callback.call(this.signal, event);
    this.signal.onabort?.(event);
    this.signal.listeners.clear();
  }
};
