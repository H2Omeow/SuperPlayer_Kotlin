export function installTimers(bridge) {
  const timers = new Map();
  let sequence = 0;

  function schedule(callback, duration, args, repeat) {
    if (typeof callback !== 'function') throw new TypeError('Timer callback must be a function');
    const milliseconds = Math.max(0, Math.min(Number(duration) || 0, 15000));
    const id = ++sequence;
    const generation = bridge.generation;
    for (const [key, timer] of timers) {
      if (timer.generation !== generation) timers.delete(key);
    }
    function enqueue() {
      const request = bridge.request('delay', { milliseconds });
      timers.set(id, { request, generation });
      request.promise.then(() => {
        if (!timers.has(id) || bridge.generation !== generation) return;
        if (!repeat) timers.delete(id);
        try {
          const returned = callback(...args);
          if (returned?.then) returned.catch((error) => {
            if (bridge.generation === generation) bridge.fail(error);
          });
          if (repeat && timers.has(id) && bridge.generation === generation) enqueue();
        } catch (error) { bridge.fail(error); }
      }, (error) => {
        timers.delete(id);
        if (bridge.generation === generation) bridge.fail(error);
      });
    }
    enqueue();
    return id;
  }

  globalThis.setTimeout = (callback, duration = 0, ...args) => schedule(callback, duration, args, false);
  globalThis.setInterval = (callback, duration = 0, ...args) => schedule(callback, duration, args, true);
  globalThis.clearTimeout = globalThis.clearInterval = (id) => {
    timers.get(id)?.request.cancel();
    timers.delete(id);
  };
}
