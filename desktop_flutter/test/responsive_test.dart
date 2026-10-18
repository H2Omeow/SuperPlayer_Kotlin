import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/player_controller.dart';
import 'package:nekoplayer_desktop/ui/neko_app.dart';
import 'package:nekoplayer_desktop/ui/neko_theme.dart';

import 'fakes.dart';

void main() {
  for (final width in [360.0, 600.0, 839.0, 840.0, 1200.0, 1600.0]) {
    testWidgets('navigation and pages fit ${width.toInt()}px', (tester) async {
      tester.view.physicalSize = Size(width, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = PlayerController(
        engine: FakeEngine(),
        store: MemoryStore(),
      );
      await controller.initialize();
      await tester.pumpWidget(
        MaterialApp(
          theme: nekoTheme(),
          home: PlayerShell(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byType(width >= 840 ? NavigationRail : NavigationBar),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      for (final label in ['音乐库', '歌单', '发现', '设置', '主页']) {
        await tester.tap(find.text(label).last);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull, reason: '$label at $width');
      }
      await tester.pumpWidget(const SizedBox());
      controller.dispose();
    });
  }

  testWidgets('large system text does not overflow compact home', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = PlayerController(
      engine: FakeEngine(),
      store: MemoryStore(),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: nekoTheme(),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
          child: child!,
        ),
        home: PlayerShell(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });

  testWidgets('short window keeps empty library and player scrollable', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(600, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = PlayerController(
      engine: FakeEngine(),
      store: MemoryStore(),
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: nekoTheme(),
        home: PlayerShell(controller: controller),
      ),
    );
    await tester.tap(find.text('音乐库').last);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(
      MaterialApp(
        theme: nekoTheme(),
        home: FullPlayer(controller: controller),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    controller.dispose();
  });
}
