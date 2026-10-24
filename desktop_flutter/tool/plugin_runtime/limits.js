import { Buffer } from 'buffer';

export const limits = Object.freeze({
  script: 5 * 1024 * 1024,
  storage: 1024 * 1024,
  result: 2 * 1024 * 1024,
  response: 2 * 1024 * 1024,
  requests: 32,
  timeout: 15000,
  initialization: 10000,
});

export function jsonText(value, maximum, label) {
  const text = JSON.stringify(value);
  if (typeof text !== 'string') throw new TypeError(`${label} must be JSON serializable`);
  if (Buffer.byteLength(text, 'utf8') > maximum) throw new Error(`${label} limit exceeded`);
  return text;
}

export function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function errorMessage(error) {
  try { return String(error?.message || error).slice(0, 500); }
  catch { return 'Plugin threw an unreadable error'; }
}
