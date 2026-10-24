import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/legacy_audio_effects.dart';
import 'package:nekoplayer_desktop/data/legacy_import.dart';
import 'package:nekoplayer_desktop/domain/audio_effects.dart';
import 'package:nekoplayer_desktop/domain/mastering_presets.dart';

List<int> message(int tag, List<int> bytes) {
  assert(bytes.length < 128);
  return [tag, bytes.length, ...bytes];
}

void main() {
  test('mastering catalog preserves Android IDs, names and native range', () {
    expect(
      MasteringPreset.available.map((preset) => preset.id),
      List.generate(20, (index) => index + 15),
    );
    expect(MasteringPreset.find(15)!.name, '惜·流年');
    expect(MasteringPreset.find(33)!.description, contains('刻意失真'));
    expect(MasteringPreset.find(34)!.name, '跨界·战歌');
    for (final preset in MasteringPreset.available) {
      expect(preset.group, isNotEmpty);
      expect(preset.description, isNotEmpty);
      expect(AudioEffects(mastering: preset.id).values[16], preset.id);
    }
    for (final id in [-1, 0, 1, 14, 35]) {
      expect(MasteringPreset.find(id), isNull);
      expect(AudioEffects(mastering: id).values[16], 0);
    }
  });

  test('all Android DSP parameters map without enabling the engine', () {
    final settings = LegacyAudioEffects.parse({
      'audio_engine': '2',
      'eq_bands': '-15,-10,-5,-1,0,1,2,5,10,15',
      'bass_boost': '21',
      'virtualizer': '22',
      'reverb_wet': '23',
      'reverb_room_size': '24',
      'reverb_damping': '25',
      'loudness_gain': '26',
      'mastering_preset_id': '33',
      'mastering_mix': '27',
    })!;
    expect(settings.enabled, isFalse);
    expect(settings.eq, [-15, -10, -5, -1, 0, 1, 2, 5, 10, 15]);
    expect(settings.values.skip(10), [21, 22, 23, 24, 25, 26, 33, 27]);
  });

  test('Android EQ preset names map to the exact desktop preset values', () {
    final settings = LegacyAudioEffects.parse({'eq_preset': '人声'})!;
    expect(settings.eq, AudioEffects.presets['人声']);
    expect(settings.enabled, isFalse);
    expect(
      () => LegacyAudioEffects.parse({'eq_preset': '不存在的预设'}),
      throwsFormatException,
    );
    final explicitBands = LegacyAudioEffects.parse({
      'eq_preset': '摇滚',
      'eq_bands': '0,0,0,0,0,0,0,0,0,0',
    })!;
    expect(explicitBands.eq, List<double>.filled(10, 0));
    expect(
      LegacyAudioEffects.parse({'eq_preset': ''})!.eq,
      List<double>.filled(10, 0),
    );
    expect(
      LegacyAudioEffects.parse({
        'eq_preset': '自定义名称',
        'eq_bands': '0,0,0,0,0,0,0,0,0,0',
      })!.eq,
      List<double>.filled(10, 0),
    );
  });

  test(
    'missing values use Android defaults but engine-only is not a preset',
    () {
      expect(LegacyAudioEffects.parse({}), isNull);
      expect(LegacyAudioEffects.parse({'audio_engine': '1'}), isNull);
      expect(LegacyAudioEffects.parse({'eq_preset': '人声'}), isNotNull);
      final partial = LegacyAudioEffects.parse({'bass_boost': '7'})!;
      expect(partial.values, [
        ...List.filled(10, 0),
        7,
        0,
        0,
        50,
        30,
        0,
        0,
        100,
      ]);
      expect(partial.enabled, isFalse);
    },
  );

  test(
    'invalid parameters and unimplemented mastering chains are rejected',
    () {
      for (final bands in [
        '',
        '0,0',
        '0,0,0,0,0,0,0,0,0,NaN',
        '0,0,0,0,0,0,0,0,0,Infinity',
        '0,0,0,0,0,0,0,0,0,16',
        '0,0,0,0,0,0,0,0,0,-16',
        '0,0,0,0,0,0,0,0,0,0,0',
      ]) {
        expect(
          () => LegacyAudioEffects.parse({'eq_bands': bands}),
          throwsFormatException,
        );
      }
      for (final key in [
        'bass_boost',
        'virtualizer',
        'reverb_wet',
        'reverb_room_size',
        'reverb_damping',
        'loudness_gain',
        'mastering_mix',
      ]) {
        for (final value in ['NaN', 'Infinity', '-1', '101', 'invalid']) {
          expect(
            () => LegacyAudioEffects.parse({key: value}),
            throwsFormatException,
          );
        }
      }
      for (final value in ['-1', '1', '14', '35', '15.5', 'NaN']) {
        expect(
          () => LegacyAudioEffects.parse({'mastering_preset_id': value}),
          throwsFormatException,
        );
      }
    },
  );

  group('effects backup opt-in', () {
    late Directory directory;
    setUp(
      () async => directory = await Directory.systemTemp.createTemp(
        'neko-effects-import-',
      ),
    );
    tearDown(() => directory.delete(recursive: true));

    test(
      'typed DataStore DSP values share the same explicit import path',
      () async {
        final bands = utf8.encode('-1,0,0,0,0,0,0,0,0,1');
        final values = {
          'bass_boost': [24, 31],
          'mastering_preset_id': [24, 15],
          'eq_bands': message(42, bands),
        };
        final original = [
          for (final entry in values.entries)
            ...message(10, [
              ...message(10, utf8.encode(entry.key)),
              ...message(18, entry.value),
            ]),
        ];
        final file = File('${directory.path}/player_prefs.preferences_pb');
        await file.writeAsBytes(original);
        final imported = await LegacyImport.readFiles([
          file.path,
        ], includeEffects: true);
        expect(imported.effects!.bass, 31);
        expect(imported.effects!.mastering, 15);
        expect(imported.effects!.eq, [-1, 0, 0, 0, 0, 0, 0, 0, 0, 1]);
        expect(imported.hasCredentials, isFalse);
        expect(imported.playback.count, 0);
        expect(await file.readAsBytes(), original);
      },
    );

    test(
      'XML effects-only import never imports credentials, queue or settings',
      () async {
        final file = File('${directory.path}/settings.xml');
        const original =
            '<map>'
            '<int name="audio_engine" value="2"/>'
            '<int name="bass_boost" value="42"/>'
            '<int name="mastering_preset_id" value="34"/>'
            '<string name="nc_user">MUSIC_U=do-not-import</string>'
            '<string name="play_queue">corrupt queue</string>'
            '<float name="playback_speed" value="1.5"/>'
            '</map>';
        await file.writeAsString(original);
        await expectLater(
          LegacyImport.readFiles([file.path]),
          throwsFormatException,
        );
        final imported = await LegacyImport.readFiles([
          file.path,
        ], includeEffects: true);
        expect(imported.effects!.bass, 42);
        expect(imported.effects!.mastering, 34);
        expect(imported.effects!.enabled, isFalse);
        expect(imported.hasCredentials, isFalse);
        expect(imported.playback.count, 0);
        expect(imported.queue, isNull);
        expect(imported.decoded.tracks, isEmpty);
        expect(await file.readAsString(), original);
      },
    );

    test(
      'unused corrupt effects cannot prevent normal music library import',
      () async {
        final file = File('${directory.path}/settings.xml');
        final library = jsonEncode([
          {'id': 1, 'nm': 'Song', 'ar': 'Artist', 'source': 'netease'},
        ]);
        await file.writeAsString(
          '<map><string name="my_favorites">$library</string>'
          '<string name="eq_bands">corrupt</string></map>',
        );
        final imported = await LegacyImport.readFiles([file.path]);
        expect(imported.decoded.tracks, hasLength(1));
        expect(imported.effects, isNull);
        await expectLater(
          LegacyImport.readFiles([file.path], includeEffects: true),
          throwsFormatException,
        );
      },
    );
  });
}
