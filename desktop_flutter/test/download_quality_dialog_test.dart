import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/ui/download_quality_dialog.dart';
import 'package:nekoplayer_desktop/ui/neko_theme.dart';

Future<void> openDialog(
  WidgetTester tester,
  Future<List<AudioQuality>> Function(bool Function()) load, {
  bool confirmation = false,
  void Function(String?)? selected,
  double scale = 1,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: nekoTheme(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(scale)),
        child: child!,
      ),
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(
            child: FilledButton(
              onPressed: () async {
                final result = await showDialog<String>(
                  context: context,
                  builder: (_) => DownloadQualityDialog(
                    load: load,
                    requiresConfirmation: confirmation,
                  ),
                );
                selected?.call(result);
              },
              child: const Text('下载'),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('下载'));
  await tester.pump();
}

void main() {
  testWidgets(
    'only verified qualities are offered with readable labels and sizes',
    (tester) async {
      String? selected;
      await openDialog(
        tester,
        (_) async => [
          const AudioQuality('flac', '无损 FLAC', 3, size: 2 * 1024 * 1024),
        ],
        selected: (value) => selected = value,
      );
      await tester.pumpAndSettle();
      expect(find.text('无损 FLAC'), findsOneWidget);
      expect(find.text('flac · 2.0 MiB'), findsOneWidget);
      expect(find.text('Hi-Res'), findsNothing);
      await tester.tap(find.text('无损 FLAC'));
      await tester.pumpAndSettle();
      expect(selected, 'flac');
    },
  );

  testWidgets('cancel invalidates a pending probe and ignores late results', (
    tester,
  ) async {
    final pending = Completer<List<AudioQuality>>();
    late bool Function() accept;
    await openDialog(tester, (callback) {
      accept = callback;
      return pending.future;
    });
    expect(accept(), isTrue);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
    expect(accept(), isFalse);
    pending.complete([const AudioQuality('flac', '无损 FLAC', 3)]);
    await tester.pumpAndSettle();
    expect(find.text('无损 FLAC'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'gateway probes require confirmation and never automatically retry',
    (tester) async {
      var calls = 0;
      await openDialog(tester, (_) async {
        calls++;
        throw const MusicSearchException('配额不足');
      }, confirmation: true);
      await tester.pumpAndSettle();
      expect(calls, 0);
      expect(find.textContaining('可能计入本站账号配额'), findsOneWidget);
      await tester.tap(find.text('检查可下载音质'));
      await tester.pumpAndSettle();
      expect(calls, 1);
      expect(find.text('配额不足'), findsOneWidget);
      await tester.pump(const Duration(seconds: 5));
      expect(calls, 1);
      await tester.tap(find.text('重新检查'));
      await tester.pumpAndSettle();
      expect(calls, 2);
    },
  );

  testWidgets(
    'empty permissions show an explicit message and no fake choices',
    (tester) async {
      await openDialog(tester, (_) async => []);
      await tester.pumpAndSettle();
      expect(find.textContaining('没有已验证的可下载完整音质'), findsOneWidget);
      expect(find.byType(ListTile), findsNothing);
      expect(find.text('重新检查'), findsOneWidget);
    },
  );

  testWidgets('all formats remain reachable in short windows with large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(600, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    String? selected;
    await openDialog(
      tester,
      (_) async => AudioQuality.supported(MusicProvider.netease),
      selected: (value) => selected = value,
      scale: 2,
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('臻音全景声'), 150);
    await tester.tap(find.text('臻音全景声'));
    await tester.pumpAndSettle();
    expect(selected, 'dolby');
    expect(tester.takeException(), isNull);
  });
}
