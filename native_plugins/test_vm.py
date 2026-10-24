import ctypes
import json
from pathlib import Path
import re
import sys
import time
import unittest
from concurrent.futures import ThreadPoolExecutor


LIBRARY_PATH = Path(sys.argv.pop(1)).resolve()
LIBRARY = ctypes.CDLL(str(LIBRARY_PATH))
LIBRARY.neko_plugin_create.argtypes = []
LIBRARY.neko_plugin_create.restype = ctypes.c_void_p
LIBRARY.neko_plugin_eval.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
LIBRARY.neko_plugin_eval.restype = ctypes.c_void_p
LIBRARY.neko_plugin_close.argtypes = [ctypes.c_void_p]
LIBRARY.neko_plugin_close.restype = None


class PluginVmTest(unittest.TestCase):
    def setUp(self):
        self.handle = LIBRARY.neko_plugin_create()
        self.assertTrue(self.handle)

    def tearDown(self):
        LIBRARY.neko_plugin_close(self.handle)

    def evaluate(self, script):
        source = script.encode("utf-8") if script is not None else None
        pointer = LIBRARY.neko_plugin_eval(self.handle, source)
        self.assertTrue(pointer)
        return json.loads(ctypes.string_at(pointer).decode("utf-8"))

    def value(self, script):
        response = self.evaluate(script)
        self.assertTrue(response["ok"], response)
        return response["value"]

    def error(self, script):
        response = self.evaluate(script)
        self.assertFalse(response["ok"], response)
        return response["error"]

    def test_values_state_and_owned_buffer(self):
        for script, expected in [("1+2", "3"), ("undefined", "undefined"),
                                 ("null", "null"), ("({answer:42})", "[object Object]"),
                                 ("123n", "123")]:
            self.assertEqual(self.value(script), expected)
        pointer = LIBRARY.neko_plugin_eval(self.handle, b"globalThis.answer=42")
        self.assertEqual(json.loads(ctypes.string_at(pointer))["value"], "42")
        time.sleep(0.01)
        self.assertEqual(json.loads(ctypes.string_at(pointer))["value"], "42")
        self.assertEqual(self.value("answer"), "42")
        other = LIBRARY.neko_plugin_create()
        try:
            result = LIBRARY.neko_plugin_eval(other, b"typeof answer")
            self.assertEqual(json.loads(ctypes.string_at(result))["value"], "undefined")
        finally:
            LIBRARY.neko_plugin_close(other)

    def test_json_unicode_and_errors(self):
        text = "中文😀\x00\n\r\t\b\f\"\\\u2028\u2029"
        self.assertEqual(self.value(json.dumps(text)), text)
        self.assertEqual(self.error("throw " + json.dumps(text)), text)
        self.assertEqual(self.value("'\\ud800'"), "\ud800")
        self.assertEqual(self.value("'\\udc00'"), "\udc00")
        self.assertIn("SyntaxError", self.error("let = ;"))
        self.assertIn("TypeError", self.error("throw new TypeError('broken')"))
        self.assertIn("converted", self.error("throw {toString(){throw 'nested'}}"))
        self.assertIn("null", self.error(None))
        self.assertEqual(self.value("40+2"), "42")

    def test_float64_arithmetic_and_coercion(self):
        for script, expected in [("'1.5' - 0.5", "1"), ("'1.5' * 2", "3"),
                                 ("'3' / 2", "1.5"), ("'3.5' % 2", "1.5"),
                                 ("'1.5' ** 2", "2.25")]:
            self.assertEqual(self.value(script), expected)

    def test_promise_jobs_and_rejections(self):
        self.value("globalThis.order=[]; Promise.resolve().then(()=>order.push(1)).then(()=>order.push(2)); 'done'")
        self.assertEqual(self.value("order.join(',')"), "1,2")
        self.value("globalThis.asyncValue=0; (async()=>{await 0;asyncValue=42})()")
        self.assertEqual(self.value("asyncValue"), "42")
        self.assertEqual(self.error("Promise.reject('rejected')"), "rejected")
        self.assertEqual(self.error("Promise.resolve().then(()=>{throw 'job failed'})"), "job failed")
        self.value("let handled=Promise.reject('handled'); Promise.resolve().then(()=>handled.catch(()=>0)); 'ok'")
        self.assertEqual(self.value("({toString(){Promise.resolve().then(()=>asyncValue=7);return 'converted'}})"), "converted")
        self.assertEqual(self.value("asyncValue"), "7")
        self.assertEqual(self.error("queueMicrotask(()=>{throw 'microtask failed'}); 'scheduled'"), "microtask failed")

    def test_job_budget(self):
        self.assertEqual(self.value("globalThis.jobs=0;for(let index=0;index<1024;index++)Promise.resolve().then(()=>jobs++);'scheduled'"), "scheduled")
        self.assertEqual(self.value("jobs"), "1024")
        started = time.monotonic()
        self.assertIn("1024", self.error("function spin(){Promise.resolve().then(spin)};spin();'scheduled'"))
        self.assertLess(time.monotonic() - started, 2)
        self.assertIn("recreate", self.error("42"))

    def test_execution_budget_and_recovery(self):
        for script in ["for(;;){}", "try{for(;;){}}catch(error){for(;;){}}",
                       "({toString(){for(;;){}}})", "throw {toString(){for(;;){}}}",
                       "Promise.resolve().then(()=>{for(;;){}})"]:
            started = time.monotonic()
            self.assertIn("750", self.error(script))
            self.assertLess(time.monotonic() - started, 2.5)
            self.assertEqual(self.value("6*7"), "42")

    def test_heap_and_stack_budget(self):
        started = time.monotonic()
        self.assertRegex(self.error("new ArrayBuffer(64*1024*1024)"), "(?i)memory")
        self.assertLess(time.monotonic() - started, 2)
        self.assertRegex(self.error("function recurse(){return recurse()+1}; recurse()"), "(?i)stack")
        self.assertEqual(self.value("21*2"), "42")

    def test_pure_context(self):
        names = ["std", "os", "require", "process", "fetch", "XMLHttpRequest",
                 "WebSocket", "Deno", "Bun", "readFile", "load", "Worker"]
        for name in names:
            self.assertEqual(self.value("typeof " + name), "undefined")
        self.assertIn("block", self.error("Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0)"))
        self.assertEqual(self.value("6*7"), "42")

    def test_secure_random(self):
        self.assertEqual(self.value("__nekoRandomHex(0)"), "")
        for length in [1, 16, 256, 65536]:
            result = self.value(f"__nekoRandomHex({length})")
            self.assertEqual(len(result), length * 2)
            self.assertIsNotNone(re.fullmatch("[0-9a-f]+", result))
        self.assertNotEqual(self.value("__nekoRandomHex(32)"), self.value("__nekoRandomHex(32)"))
        for argument in ["-1", "65537", "1.5", "NaN", "Infinity", "'2'", "null", "undefined", "1n", ""]:
            self.error(f"__nekoRandomHex({argument})")

    def test_vm_migrates_threads(self):
        self.value("globalThis.counter=0")
        with ThreadPoolExecutor(max_workers=1) as executor:
            self.assertEqual(executor.submit(self.value, "++counter").result(), "1")
        self.assertEqual(self.value("++counter"), "2")


if __name__ == "__main__":
    unittest.main(verbosity=2)
