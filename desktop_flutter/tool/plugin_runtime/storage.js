import { isRecord, jsonText, limits } from './limits.js';

export function createStorage(initial = {}) {
  if (!isRecord(initial)) throw new TypeError('Plugin storage must be an object');
  let data = Object.assign(Object.create(null), JSON.parse(jsonText(initial, limits.storage, 'Plugin storage')));
  const api = Object.freeze({
    async getItem(key) {
      const name = String(key);
      return Object.prototype.hasOwnProperty.call(data, name) ? JSON.parse(JSON.stringify(data[name])) : null;
    },
    async setItem(key, value) {
      if (value === undefined) throw new TypeError('Plugin storage values must be JSON serializable');
      const next = Object.assign(Object.create(null), data, { [String(key)]: value });
      data = Object.assign(Object.create(null), JSON.parse(jsonText(next, limits.storage, 'Plugin storage')));
    },
    async removeItem(key) { delete data[String(key)]; },
  });
  return { api, snapshot: () => data };
}
