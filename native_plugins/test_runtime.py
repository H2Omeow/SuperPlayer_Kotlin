import base64
import ctypes
import json
from pathlib import Path
import sys
import unittest


LIBRARY = ctypes.CDLL(str(Path(sys.argv.pop(1)).resolve()))
LIBRARY.neko_plugin_create.restype = ctypes.c_void_p
LIBRARY.neko_plugin_eval.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
LIBRARY.neko_plugin_eval.restype = ctypes.c_char_p
LIBRARY.neko_plugin_close.argtypes = [ctypes.c_void_p]
BUNDLE = Path(__file__).resolve().parents[1] / 'desktop_flutter/assets/music_plugins/runtime.js'


class RuntimeTest(unittest.TestCase):
    def setUp(self):
        self.handle = LIBRARY.neko_plugin_create()
        self.evaluate(BUNDLE.read_text())

    def tearDown(self):
        LIBRARY.neko_plugin_close(self.handle)

    def evaluate(self, script):
        result = json.loads(LIBRARY.neko_plugin_eval(self.handle, script.encode()))
        self.assertTrue(result['ok'], result)
        return result['value']

    def finish(self, responder=None):
        for attempt in range(30):
            result = json.loads(self.evaluate('NekoPlugin.take()'))
            if result['state'] != 'pending':
                self.assertEqual(result['state'], 'done', result)
                return result
            for request in result['requests']:
                self.assertIsNotNone(responder, request)
                response = responder(request)
                self.evaluate(f'NekoPlugin.deliver({request["id"]},true,{json.dumps(response)});')
        self.fail('Plugin did not settle')

    def load(self, script, **options):
        self.evaluate(f'NekoPlugin.load({json.dumps(script)},{json.dumps(options)});')
        return self.finish()['value']

    def invoke(self, method, args=None, responder=None):
        self.evaluate(f'NekoPlugin.invoke({json.dumps(method)},{json.dumps(args or [])});')
        return self.finish(responder)

    def response(self, request, body, status=200):
        return {'status': status, 'url': request['payload']['url'], 'headers': {'content-type': 'application/json'},
                'body': base64.b64encode(body.encode()).decode()}

    def test_musicfree_search_play_lyric_http(self):
        metadata = self.load('''
          const axios=require('axios').create({baseURL:'https://example.test'});
          axios.interceptors.request.use(config=>{config.headers['X-Plugin']='yes';return config});
          module.exports={platform:'测试音乐', version:'1',
            async search(query,page,type){return (await axios.get('/search',{params:{query,page,type}})).data},
            async getMediaSource(song,quality){return (await axios.post('/play',{id:song.id,token:song.token,quality})).data},
            async getLyric(){return {rawLrc:'[00:01]歌词'}}};
        ''')
        self.assertEqual(metadata['format'], 'musicfree')
        self.assertIn('search', metadata['capabilities'])
        def search(request):
            self.assertIn('query=%E6%B5%8B%E8%AF%95', request['payload']['url'])
            self.assertEqual(request['payload']['headers']['X-Plugin'], 'yes')
            return self.response(request, '{"data":[{"id":"1","title":"歌","token":"raw"}],"isEnd":true}')
        songs = self.invoke('search', ['测试', 1, 'music'], search)['value']
        def play(request):
            self.assertEqual(json.loads(request['payload']['body']), {'id': '1', 'token': 'raw', 'quality': 'standard'})
            return self.response(request, '{"url":"https://media.test/a.mp3","headers":{"Referer":"https://example.test"}}')
        result = self.invoke('getMediaSource', [songs['data'][0], 'standard'], play)
        self.assertEqual(result['value']['headers']['Referer'], 'https://example.test')
        self.assertEqual(self.invoke('getLyric')['value']['rawLrc'], '[00:01]歌词')
        self.assertEqual(self.invoke('__nekoFetchLyric', ['https://example.test/lyric'],
                                     lambda request: self.response(request, '[00:01]远程歌词'))['value'], '[00:01]远程歌词')

    def test_baka_variables_storage_crypto_and_modules(self):
        metadata = self.load('''
          module.exports={platform:'Baka fixture',supportedQualities:['128k','flac24bit'],
            async search(){
              const store=require('musicfree/storage'); await store.setItem('__proto__',{safe:true});
              return {token:env.getUserVariables().token, os:env.os, stored:await store.getItem('__proto__'),
                hash:require('crypto-js').MD5('abc').toString(),
                title:require('cheerio').load('<h1>中文</h1>')('h1').text(),
                random:require('crypto-js').lib.WordArray.random(16).sigBytes,
                number:require('big-integer')('9007199254740993').add(1).toString(),
                html:require('he').decode('&amp;'),
                day:require('dayjs')('2026-10-25').format('YYYY-MM-DD')};
            }};
        ''', variables={'token': 'configured'}, os='linux')
        self.assertEqual(metadata['format'], 'baka')
        result = self.invoke('search')
        self.assertEqual(result['value'], {'token': 'configured', 'os': 'linux', 'stored': {'safe': True},
                         'hash': '900150983cd24fb0d6963f7d28e17f72', 'title': '中文', 'random': 16,
                         'number': '9007199254740994', 'html': '&', 'day': '2026-10-25'})
        self.assertEqual(result['storage']['__proto__'], {'safe': True})

    def test_lx_registration_request_crypto_and_compression(self):
        metadata = self.load('''
          /**
           * @name 测试 LX
           * @version 2.0
           */
          lx.on(lx.EVENT_NAMES.request,async({source,action,info})=>{
            const text=await new Promise((resolve,reject)=>lx.request('https://example.test/lx',
              {method:'POST',form:{id:info.musicInfo.songmid,quality:info.type}},
              (error,response,body)=>error?reject(error):resolve(body.url)));
            const compressed=await lx.utils.zlib.deflate('中文');
            if(lx.utils.buffer.bufToString(await lx.utils.zlib.inflate(compressed),'utf8')!=='中文')throw Error('zlib');
            if(lx.utils.crypto.md5('abc')!=='900150983cd24fb0d6963f7d28e17f72')throw Error('md5');
            if(lx.utils.crypto.randomBytes(16).length!==16)throw Error('random');
            return text;
          });
          lx.send(lx.EVENT_NAMES.inited,{status:true,sources:{kg:{name:'酷狗',type:'music',actions:['musicUrl'],qualitys:['128k','320k','flac']}}});
        ''', format='lx')
        self.assertEqual(metadata['name'], '测试 LX')
        self.assertIn('kg', metadata['sources'])
        def request(request):
            self.assertEqual(request['payload']['body'], 'id=hash&quality=320k')
            return self.response(request, '{"url":"https://media.test/lx.mp3"}')
        result = self.invoke('lxRequest', ['kg', 'musicUrl', {'type': '320k', 'musicInfo': {'songmid': 'hash'}}], request)
        self.assertEqual(result['value'], 'https://media.test/lx.mp3')

    def test_webdav_directory_uses_host_http(self):
        self.load('''module.exports={platform:'dav',search:async()=>
            require('webdav').createClient('https://example.test/dav')
                .getDirectoryContents('/',{details:true})};''')
        def directory(request):
            self.assertEqual(request['payload']['method'], 'PROPFIND')
            self.assertEqual(request['payload']['headers']['Depth'], '1')
            return self.response(request, '''<?xml version="1.0"?>
              <d:multistatus xmlns:d="DAV:"><d:response><d:href>/dav/song.lrc</d:href>
              <d:propstat><d:prop><d:displayname>song.lrc</d:displayname>
              <d:resourcetype/><d:getcontentlength>42</d:getcontentlength>
              <d:getcontenttype>text/plain</d:getcontenttype></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>''', 207)
        result = self.invoke('search', responder=directory)['value']
        self.assertEqual(result['status'], 207)
        self.assertEqual(result['data'][0]['basename'], 'song.lrc')

    def test_timer_and_unsupported_module(self):
        self.load('''module.exports={platform:'timer',async search(){
            await new Promise(resolve=>setTimeout(resolve,5));return typeof process.exit;}}''')
        result = self.invoke('search', responder=lambda request: self.assertEqual(request['kind'], 'delay'))
        self.assertEqual(result['value'], 'undefined')
        self.evaluate('NekoPlugin.load(' + json.dumps('require("fs");module.exports={platform:"bad"}') + ',{});')
        state = json.loads(self.evaluate('NekoPlugin.take()'))
        self.assertEqual(state['state'], 'error')
        self.assertIn('Unsupported plugin module', state['error'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
