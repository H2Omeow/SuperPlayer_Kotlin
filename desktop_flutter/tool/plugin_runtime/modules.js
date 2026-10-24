import { Buffer } from 'buffer';
import * as cheerio from 'cheerio';
import dayjs from 'dayjs';
import bigInt from 'big-integer';
import qs from 'qs';
import he from 'he';
import pako from 'pako';
import * as webdav from 'webdav/web';
import { CryptoJS, cryptoModule } from './crypto.js';

export function createRequire(axios, storage, fetch) {
  const cheerioModule = { ...cheerio };
  webdav.getPatcher().patch('fetch', fetch);
  const cookies = Object.fromEntries(['get', 'set', 'flush', 'clearAll', 'getAll', 'clearByName', 'setFromResponse', 'getFromResponse']
    .map((method) => [method, async () => { throw new Error('Native cookie access is unsupported; use explicit plugin HTTP headers'); }]));
  const modules = new Map(Object.entries({
    axios, cheerio: cheerioModule, 'crypto-js': CryptoJS, dayjs, 'big-integer': bigInt,
    qs, he, pako, buffer: { Buffer, SlowBuffer: Buffer.SlowBuffer, INSPECT_MAX_BYTES: 50 },
    crypto: cryptoModule, 'musicfree/storage': storage, webdav: { ...webdav },
    '@react-native-cookies/cookies': cookies,
  }));
  for (const value of modules.values()) {
    if (Object.isExtensible(value) && !Object.prototype.hasOwnProperty.call(value, 'default')) {
      Object.defineProperty(value, 'default', { value, enumerable: false });
    }
  }
  return (name) => {
    if (typeof name !== 'string' || !modules.has(name)) throw new Error(`Unsupported plugin module: ${String(name)}`);
    return modules.get(name);
  };
}
