import { isRecord } from './limits.js';

export const musicFreeQualities = ['low', 'standard', 'high', 'super'];
export const bakaQualities = ['96k', '128k', '192k', '320k', 'flac', 'flac24bit', 'hires', 'vinyl', 'dolby', 'atmos', 'atmos_plus', 'master'];

export function loadCommonJS(script, options, requirePlugin) {
  const environment = {
    os: options.os === 'windows' ? 'win32' : options.os || 'linux', appVersion: options.appVersion || '1.2.0', lang: options.lang || 'zh-CN',
    getUserVariables: () => options.variables || {},
    get userVariables() { return this.getUserVariables(); },
  };
  const module = { exports: {} };
  const returned = new Function('require', '__musicfree_require', 'module', 'exports', 'console', 'env', 'URL', 'process',
    `'use strict';\n${script}`)(requirePlugin, requirePlugin, module, module.exports, globalThis.console,
    environment, globalThis.URL, { env: environment, platform: environment.os, version: environment.appVersion });
  const exported = module.exports;
  const plugin = exported && Object.prototype.hasOwnProperty.call(exported, 'default') ? exported.default
    : exported && Object.keys(exported).length ? exported : returned;
  if (!isRecord(plugin) || typeof plugin.platform !== 'string' || !plugin.platform.trim() || plugin.platform.length > 128) {
    throw new Error('Plugin must export a platform name between 1 and 128 characters');
  }
  let qualities = plugin.supportedQualities;
  if (qualities !== undefined && (!Array.isArray(qualities) || qualities.some((quality) => typeof quality !== 'string'))) {
    throw new Error('Plugin supportedQualities must be an array of strings');
  }
  const format = options.format || (qualities?.some((quality) => bakaQualities.includes(quality)) ? 'baka' : 'musicfree');
  const allowed = format === 'baka' ? bakaQualities : musicFreeQualities;
  qualities = qualities === undefined ? (format === 'musicfree' ? [...musicFreeQualities] : []) : [...new Set(qualities)];
  if (qualities.some((quality) => !allowed.includes(quality))) throw new Error(`Invalid ${format} supportedQualities`);
  if (plugin.userVariables !== undefined && !Array.isArray(plugin.userVariables)) throw new Error('Plugin userVariables must be an array');
  const descriptor = {
    format, name: plugin.platform, platform: plugin.platform,
    version: String(plugin.version || ''), author: String(plugin.author || ''),
    capabilities: Object.keys(plugin).filter((key) => typeof plugin[key] === 'function'),
    qualities, userVariables: plugin.userVariables || [],
  };
  return {
    descriptor,
    invoke(method, args) {
      if (!Object.prototype.hasOwnProperty.call(plugin, method) || typeof plugin[method] !== 'function') {
        throw new Error(`Plugin does not support ${method}`);
      }
      return plugin[method](...args);
    },
  };
}
