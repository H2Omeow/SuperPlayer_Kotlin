import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/ui/timed_lyric_text.dart';

void main() {
  test(
    'LRC export preserves millisecond timing and gives every translation its own tag',
    () {
      const lines = [
        LyricLine(
          Duration(milliseconds: 62123),
          'Main',
          translation: '翻译一\n翻译二',
        ),
        LyricLine(Duration.zero, 'Beginning'),
      ];
      final rendered = renderLrc(lines);
      expect(
        rendered,
        '[01:02.123]Main\n[01:02.123]翻译一\n[01:02.123]翻译二\n[00:00.000]Beginning',
      );
      expect(parseLrc(rendered).last.translation, '翻译一\n翻译二');
      expect(parseLrc(rendered).last.time, const Duration(milliseconds: 62123));
    },
  );
  test(
    'Android YRC and JSON metadata retain zero-time lines and word timing',
    () {
      final lines = parseLrc('''
{"t":0,"c":[{"tx":"演唱："},{"tx":"Neko"}]}
[1000,2000](1000,500,0)你(1500,1500,0)好
[4000,1000]纯文本
''');
      expect(lines.map((line) => line.text), ['演唱：Neko', '你好', '纯文本']);
      expect(lines[1].time, const Duration(seconds: 1));
      expect(lines[1].words.map((word) => word.text), ['你', '好']);
      expect(lines[1].words[1].time, const Duration(milliseconds: 1500));
      expect(lines[1].words[1].duration, const Duration(milliseconds: 1500));
      expect(lines[1].words[0].progress(Duration.zero), 0);
      expect(
        lines[1].words[0].progress(const Duration(milliseconds: 1250)),
        .5,
      );
      expect(lines[1].words[0].progress(const Duration(seconds: 10)), 1);
      expect(
        const LyricWord(
          '字',
          Duration.zero,
          Duration.zero,
        ).progress(Duration.zero),
        1,
      );
    },
  );

  test(
    'offset applies to both YRC line and words without breaking legacy LRC',
    () {
      final lines = parseLrc(
        '[offset:-200]\n[1000,1000](1000,500,0)Hello\n[00:03]World',
      );
      expect(lines.first.time.inMilliseconds, 800);
      expect(lines.first.words.single.time.inMilliseconds, 800);
      expect(lines.last.time.inMilliseconds, 2800);
    },
  );

  test('translation chooses the nearest line strictly within half a second', () {
    final lines = parseLrc(
      '[1000,1000](1000,500,0)Hello\n[3000,1000](3000,500,0)World\n[5000,1000]End',
      translation:
          '[00:00.700]你好\n[00:01.100]您好\n[00:03.499]世界\n[00:05.500]不应合并',
    );
    expect(lines.map((line) => line.translation), ['您好', '世界', '']);
    expect(lines.first.words, hasLength(1));
    final roundTrip = parseLrc(
      encodeLyrics('[1000,1000](1000,500,0)Hello', '[00:01.1]你好'),
    );
    expect(roundTrip.single.translation, '你好');
    expect(roundTrip.single.words.single.text, 'Hello');
  });

  test('malformed metadata is ignored without losing later valid lines', () {
    final lines = parseLrc(
      '{bad json}\n{"t":"NaN","c":[]}\n{"t":2,"c":[null,{}, {"tx":7}]}\n[1000,500](1000,0,0)Valid',
    );
    expect(lines.single.text, 'Valid');
    expect(lines.single.words.single.duration, Duration.zero);
  });

  const song = OnlineSong(
    provider: MusicProvider.netease,
    id: '1',
    title: 'Hello',
    artist: 'Neko',
  );
  test(
    'native lyrics prefer YRC and preserve separately timed translation',
    () async {
      final paths = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          paths.add(request.uri.path);
          return {
            'code': 200,
            'yrc': {'lyric': '[1000,1000](1000,500,0)Hello'},
            'lrc': {'lyric': '[00:01]Fallback'},
            'tlyric': {'lyric': '[00:01.1]你好'},
          };
        },
      );
      final lines = parseLrc(await catalog.lyrics(song));
      expect(paths, ['/eapi/song/lyric/v1']);
      expect(lines.single.text, 'Hello');
      expect(lines.single.translation, '你好');
      expect(lines.single.words, hasLength(1));
    },
  );

  for (final failure in ['empty', 'error']) {
    test(
      'native lyrics fall back to standard endpoint on $failure response',
      () async {
        final paths = <String>[];
        final catalog = NativeMusicCatalog(
          initializeSessions: false,
          transport: (request) async {
            paths.add(request.uri.path);
            if (paths.length == 1) {
              return failure == 'empty' ? {'code': 200} : {'code': 500};
            }
            return {
              'code': 200,
              'lrc': {'lyric': '[00:01]Hello'},
              'tlyric': {'lyric': '[00:01.1]你好'},
            };
          },
        );
        final lines = parseLrc(await catalog.lyrics(song));
        expect(paths, ['/eapi/song/lyric/v1', '/eapi/song/lyric']);
        expect(lines.single.translation, '你好');
      },
    );
  }

  testWidgets(
    'active word colors follow progress and translation can be hidden',
    (tester) async {
      final line = parseLrc(
        '[1000,2000](1000,500,0)你(1500,1500,0)好',
        translation: '[00:01]Hello',
      ).single;
      Future<void> show(Duration position, {bool translation = true}) =>
          tester.pumpWidget(
            MaterialApp(
              home: Scaffold(
                body: TimedLyricText(
                  line: line,
                  position: position,
                  active: true,
                  showTranslation: translation,
                ),
              ),
            ),
          );
      await show(const Duration(milliseconds: 1250));
      TextSpan content() =>
          tester.widget<Text>(find.byType(Text).first).textSpan! as TextSpan;
      final first = content().children![0] as TextSpan;
      final second = content().children![1] as TextSpan;
      expect(first.style!.color, isNot(second.style!.color));
      expect(content().toPlainText(), '你好\nHello');
      await show(const Duration(seconds: 4), translation: false);
      expect(content().toPlainText(), '你好');
      expect(
        (content().children![0] as TextSpan).style!.color,
        (content().children![1] as TextSpan).style!.color,
      );
      expect(tester.takeException(), isNull);
    },
  );
}
