import { Buffer } from 'buffer';
import { createHttp } from './http.js';
import { createStorage } from './storage.js';
import { createRequire } from './modules.js';
import { loadCommonJS } from './commonjs.js';
import { loadLx } from './lx.js';
import { errorMessage, isRecord, jsonText, limits } from './limits.js';

export function createRuntime(bridge) {
  let state = 'error';
  let value = null;
  let error = 'No plugin loaded';
  let plugin;
  let storage = createStorage();
  const http = createHttp(bridge);
  globalThis.fetch = http.fetch;

  function fail(failure) {
    state = 'error';
    error = errorMessage(failure);
  }
  bridge.setFailureHandler(fail);

  function operate(operation) {
    bridge.reset();
    state = 'pending';
    value = null;
    error = null;
    const generation = bridge.generation;
    try {
      Promise.resolve(operation()).then((result) => {
        if (generation !== bridge.generation || state === 'error') return;
        value = JSON.parse(jsonText(result === undefined ? null : result, limits.result, 'Plugin result'));
        state = 'done';
      }).catch((failure) => { if (generation === bridge.generation) fail(failure); });
    } catch (failure) { fail(failure); }
  }

  return {
    load(script, options = {}) {
      operate(async () => {
        plugin = null;
        if (typeof script !== 'string' || Buffer.byteLength(script) > limits.script) throw new Error('Invalid plugin script size');
        if (!isRecord(options)) throw new Error('Plugin options must be an object');
        storage = createStorage(options.storage || {});
        const format = options.format || (/\blx\s*[.\[]/.test(script) ? 'lx' : null);
        if (format && !['musicfree', 'baka', 'lx'].includes(format)) throw new Error('Unsupported plugin format');
        plugin = format === 'lx'
          ? await loadLx(script, options, http, bridge)
          : loadCommonJS(script, options, createRequire(http.axios, storage.api, http.fetch));
        return plugin.descriptor;
      });
    },
    invoke(method, args) {
      operate(() => {
        if (!plugin) throw new Error('No plugin loaded');
        if (!Array.isArray(args)) throw new TypeError('Plugin arguments must be an array');
        if (method === '__nekoFetchLyric') return http.axios.get(String(args[0]), { responseType: 'text' }).then((response) => response.data);
        return plugin.invoke(method, args);
      });
    },
    take() {
      bridge.expire();
      return jsonText({ state, value, error, requests: state === 'pending' ? bridge.drain() : [], storage: storage.snapshot() },
        limits.result + limits.storage + limits.response, 'Plugin host message');
    },
    deliver(id, success, result) { bridge.deliver(id, success, result); },
  };
}
