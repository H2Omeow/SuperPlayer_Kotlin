import { limits } from './limits.js';

export function createBridge() {
  let sequence = 0;
  let generation = 0;
  let outgoing = [];
  const pending = new Map();
  let failureHandler = () => {};

  function cancel(id, error) {
    const request = pending.get(id);
    if (!request) return;
    pending.delete(id);
    outgoing = outgoing.filter((entry) => entry.id !== id);
    if (error) request.reject(error);
  }

  return {
    get generation() { return generation; },
    setFailureHandler(handler) { failureHandler = handler; },
    fail(error) { failureHandler(error); },
    reset() {
      generation++;
      pending.clear();
      outgoing = [];
    },
    request(kind, payload, timeout = 0) {
      if (pending.size >= limits.requests) throw new Error('Too many plugin requests');
      const id = ++sequence;
      const promise = new Promise((resolve, reject) => {
        pending.set(id, { resolve, reject, deadline: timeout ? Date.now() + timeout : Infinity });
      });
      outgoing.push({ id, kind, payload });
      return { id, promise, cancel: (error) => cancel(id, error) };
    },
    expire() {
      for (const [id, request] of pending) {
        if (Date.now() < request.deadline) continue;
        const error = new Error('Plugin HTTP request timed out');
        error.code = 'ECONNABORTED';
        cancel(id, error);
      }
    },
    deliver(id, success, value) {
      this.expire();
      const request = pending.get(id);
      if (!request) return;
      pending.delete(id);
      if (success === true) request.resolve(value);
      else request.reject(new Error(typeof value === 'string' ? value : 'Host request failed'));
    },
    drain() {
      const requests = outgoing;
      outgoing = [];
      return requests;
    },
  };
}
