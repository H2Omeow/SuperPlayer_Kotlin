import './platform.js';
import { URL, URLSearchParams } from 'whatwg-url';
import { createBridge } from './bridge.js';
import { installTimers } from './timers.js';
import { createRuntime } from './runtime.js';

globalThis.URL = URL;
globalThis.URLSearchParams = URLSearchParams;
globalThis.console = Object.freeze(Object.fromEntries(
  ['log', 'warn', 'error', 'debug', 'info', 'trace', 'assert'].map((method) => [method, () => {}]),
));

const bridge = createBridge();
installTimers(bridge);
globalThis.NekoPlugin = Object.freeze(createRuntime(bridge));
