import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

const [url, output] = process.argv.slice(2);
assert(url && output, "Usage: node smoke_dynamic_lyrics.mjs URL OUTPUT_DIR");
const profile = await mkdtemp(join(tmpdir(), "neko-lyric-chromium-"));
const browser = spawn(process.env.CHROMIUM_BIN || "chromium", [
  "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
  "--disable-background-networking", "--disable-component-update",
  "--disable-sync", "--disable-extensions", "--no-first-run",
  "--no-default-browser-check", "--renderer-process-limit=1",
  "--enable-unsafe-swiftshader", "--use-angle=swiftshader",
  "--js-flags=--max-old-space-size=192", "--remote-debugging-port=0",
  `--user-data-dir=${profile}`, "about:blank"
], { stdio: ["ignore", "ignore", "pipe"] });
let stderr = "";
browser.stderr.on("data", data => { stderr = (stderr + data).slice(-10000); });
const deadline = setTimeout(() => browser.kill("SIGKILL"), 85000);
let socket;
try {
  async function until(callback, timeout = 20000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) {
      const value = await callback();
      if (value) return value;
      if (browser.exitCode !== null) throw new Error(stderr);
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw new Error("Browser condition timed out\n" + stderr);
  }
  const devtools = await until(() => stderr.match(/DevTools listening on (ws:\/\/[^\s]+)/)?.[1]);
  const port = new URL(devtools).port;
  const pages = await fetch(`http://127.0.0.1:${port}/json/list`).then(response => response.json());
  socket = new WebSocket(pages.find(page => page.type === "page").webSocketDebuggerUrl);
  await once(socket, "open");
  let serial = 0;
  const requests = new Map();
  const exceptions = [];
  const external = [];
  socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const pending = requests.get(message.id);
      requests.delete(message.id);
      if (message.error) pending.reject(new Error(JSON.stringify(message.error)));
      else pending.resolve(message.result);
    }
    if (message.method === "Runtime.exceptionThrown") {
      exceptions.push(JSON.stringify(message.params.exceptionDetails));
    }
    if (message.method === "Network.requestWillBeSent") {
      const resource = message.params.request.url;
      if (/^https?:/.test(resource) && !resource.startsWith(new URL(url).origin + "/")) {
        external.push(resource);
      }
    }
  });
  function send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++serial;
      requests.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });
  }
  async function evaluate(expression) {
    const response = await send("Runtime.evaluate", { expression, returnByValue: true, awaitPromise: true });
    assert(!response.exceptionDetails, JSON.stringify(response.exceptionDetails));
    return response.result.value;
  }
  await send("Runtime.enable");
  await send("Network.enable");
  await send("Page.enable");
  await send("Emulation.setDeviceMetricsOverride", { width: 1100, height: 760, deviceScaleFactor: 1, mobile: false });
  await send("Page.navigate", { url });
  await until(() => evaluate('Boolean(window.__pony?.ready && document.querySelector("#root")?.children.length && document.querySelector("#track")?.textContent.includes("中文歌曲"))'));
  assert.equal(await evaluate('document.querySelector("#status").textContent'), "同步播放中");
  const initial = await fetch(new URL("state.json", url)).then(response => response.json());
  await send("Page.navigate", { url: url + "?native=1" });
  await until(() => evaluate('Boolean(window.__pony?.ready && window.nekoLyrics)'));
  initial.clock = { ...initial.clock, positionSec: 2.2, anchoredAtMs: Date.now(), playing: false };
  await evaluate(`window.nekoLyrics.push(${JSON.stringify(initial)})`);
  await until(() => evaluate('document.querySelector("#root")?.textContent.includes("中文歌词")'));
  assert.equal(await evaluate('document.querySelector("#status").textContent'), "已暂停");
  await evaluate('document.fonts.ready');
  const screenshot = await send("Page.captureScreenshot", { format: "png" });
  await writeFile(join(output, "dynamic-lyrics.png"), Buffer.from(screenshot.data, "base64"));
  for (const mode of ["sonnet", "cappella", "monet", "still", "partita", "tilt", "cadenza", "claddagh", "diorama", "fume", "lumiere", "pendolo", "tempera", "classic"]) {
    await evaluate(`document.querySelector("#mode").value = ${JSON.stringify(mode)}; document.querySelector("#mode").dispatchEvent(new Event("change"));`);
    await new Promise(resolve => setTimeout(resolve, 350));
    assert(await evaluate('Boolean(document.querySelector("#root")?.children.length)'), mode);
  }
  initial.track.name = '切歌 <script>throw "unsafe"</script>';
  initial.clock = { ...initial.clock, positionSec: 4.5, playbackRate: 0.75, playing: true, anchoredAtMs: Date.now() };
  await evaluate(`window.nekoLyrics.push(${JSON.stringify(initial)})`);
  assert((await evaluate('document.querySelector("#track").textContent')).includes("<script>"));
  assert.equal(await evaluate('document.querySelector("#track").children.length'), 0);
  await evaluate('window.nekoLyrics.suspend()');
  assert.equal(await evaluate('document.querySelector("#status").textContent'), "播放器已断开，歌词已暂停");
  await evaluate('window.nekoLyrics.push({track: null, lyrics: null, clock: {positionSec: 0, durationSec: 0, playing: false, anchoredAtMs: Date.now(), playbackRate: 1}})');
  assert.equal(await evaluate('document.querySelector("#status").textContent'), "尚未播放");
  assert.deepEqual(exceptions, []);
  assert.deepEqual(external, []);
  console.log("PASS: actual Folia renderer, 14 modes, Chinese/timed lyrics, pause/seek/rate/track/clear, no external requests");
} finally {
  clearTimeout(deadline);
  socket?.close();
  browser.kill("SIGTERM");
  await Promise.race([once(browser, "exit"), new Promise(resolve => setTimeout(resolve, 3000))]);
  if (browser.exitCode === null) browser.kill("SIGKILL");
  await rm(profile, { recursive: true, force: true, maxRetries: 3 });
}
