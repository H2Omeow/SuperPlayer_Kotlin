(() => {
  "use strict";
  const native = new URLSearchParams(location.search).get("native") === "1";
  const status = document.getElementById("status");
  const title = document.getElementById("track");
  const mode = document.getElementById("mode");
  let snapshot = null;
  let disconnected = false;
  let timer;
  let polling = false;

  function render() {
    if (!snapshot) return;
    const hidden = document.hidden || disconnected;
    const update = {
      ...snapshot,
      clock: { ...snapshot.clock, playing: !hidden && snapshot.clock.playing },
      appearance: {
        ...snapshot.appearance,
        mode: mode.value,
        transparent: true,
        isDaylight: false,
        staticMode: hidden || !snapshot.clock.playing,
        lyricsFontStyle: { fontFamily: "Neko CJK" },
        subtitleFontStyle: { fontFamily: "Neko CJK" }
      }
    };
    title.textContent = snapshot.track
      ? [snapshot.track.name, snapshot.track.artist].filter(Boolean).join(" · ")
      : "NekoPlayer";
    status.textContent = disconnected ? "播放器已断开，歌词已暂停"
      : !snapshot.track ? "尚未播放"
      : !snapshot.lyrics?.lines?.length ? "当前歌曲暂无歌词"
      : snapshot.clock.playing ? "同步播放中" : "已暂停";
    if (window.__pony?.ready) window.__pony.push(update);
    else window.__ponyPending = update;
    document.title = "NekoPlayer · 动态歌词";
  }

  window.nekoLyrics = {
    push(value) {
      if (!value || typeof value !== "object" || !value.clock) return;
      snapshot = value;
      disconnected = false;
      render();
    },
    suspend() {
      disconnected = true;
      render();
    }
  };

  async function poll() {
    clearTimeout(timer);
    if (polling) return;
    polling = true;
    if (!document.hidden) {
      try {
        const response = await fetch("./state.json", {
          cache: "no-store",
          credentials: "omit",
          signal: AbortSignal.timeout(4000)
        });
        if (!response.ok) throw new Error("Disconnected");
        window.nekoLyrics.push(await response.json());
      } catch (_) {
        window.nekoLyrics.suspend();
        if (!snapshot) status.textContent = "未连接播放器，请从播放器重新打开";
      }
    }
    polling = false;
    timer = setTimeout(poll, document.hidden ? 2000 : disconnected ? 2000 : 500);
  }

  mode.addEventListener("change", render);
  document.addEventListener("visibilitychange", () => {
    render();
    if (!native) void poll();
  });
  if (!native) void poll();
})();
