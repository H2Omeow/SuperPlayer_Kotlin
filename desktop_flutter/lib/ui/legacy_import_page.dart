import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../data/account_store.dart';
import '../data/legacy_import.dart';
import '../player_controller.dart';
import '../playback/effects_playback_engine.dart';

class LegacyImportPage extends StatefulWidget {
  const LegacyImportPage({
    super.key,
    required this.player,
    required this.accounts,
  });
  final PlayerController player;
  final AccountStore accounts;
  @override
  State<LegacyImportPage> createState() => _LegacyImportPageState();
}

class _LegacyImportPageState extends State<LegacyImportPage> {
  bool includeCredentials = false;
  bool includeSettings = false;
  bool includeQueue = false;
  bool includeEffects = false;
  bool busy = false;
  LegacyImport? preview;
  String? message;

  Future<void> select() async {
    if (busy) return;
    setState(() {
      busy = true;
      message = null;
      preview = null;
    });
    try {
      final files = await openFiles(
        acceptedTypeGroups: [
          const XTypeGroup(
            label: '旧版备份',
            extensions: ['properties', 'xml', 'json', 'pb', 'preferences_pb'],
          ),
        ],
      );
      if (!mounted || files.isEmpty) return;
      final imported = await LegacyImport.readFiles(
        files.map((file) => file.path).toList(),
        includeCredentials: includeCredentials,
        includeSettings: includeSettings,
        includeQueue: includeQueue,
        includeEffects: includeEffects,
      );
      if (mounted) setState(() => preview = imported);
    } on FormatException catch (failure) {
      if (mounted) setState(() => message = failure.message);
    } catch (_) {
      if (mounted) setState(() => message = '未能读取备份，请检查文件权限和备份格式。');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> apply() async {
    final imported = preview;
    if (busy || imported == null) return;
    setState(() {
      busy = true;
      message = null;
    });
    var librarySaved = false;
    try {
      await widget.player.mergeCloud(imported.library);
      librarySaved = true;
      final accounts = await imported.fillMissingAccounts(widget.accounts);
      if (imported.queue != null) {
        await widget.player.importPlaybackQueue(
          imported.queue!.tracks,
          imported.queue!.session,
        );
      }
      if (imported.playback.count > 0) {
        await widget.player.importPlaybackPreferences(
          speed: imported.playback.rate,
          quality: imported.playback.quality,
          translation: imported.playback.translation,
          twoLines: imported.playback.twoLines,
        );
      }
      if (imported.effects != null) {
        final engine = widget.player.engine;
        if (engine is! EffectsPlaybackEngine) {
          throw StateError('当前播放引擎不支持音效导入。');
        }
        await engine.importParameters(imported.effects!);
      }
      if (mounted) {
        setState(() {
          preview = null;
          message =
              '音乐库合并完成，导入 $accounts 份空缺账号凭据、${imported.playback.count} 项播放设置。${imported.queue == null ? '' : '旧版队列和进度已保存，点击播放后继续。'}${imported.effects == null ? '' : '音效参数已保存，专业模式开关保持不变。'}原备份文件未修改；未访问云端。';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => message = librarySaved
              ? '音乐库已保存，但部分凭据、播放设置、队列或音效应用失败。已保存的内容不会回滚；检查密钥环、空间和播放设备后可安全重试。'
              : '本机音乐库保存失败，未继续导入账号，请检查存储权限和空间。',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('导入旧版备份')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const Text('只读取你主动选择的文件，不扫描旧版目录，也不覆盖或上传原文件。'),
            const SizedBox(height: 16),
            const Text(
              '支持 Compose 桌面端 .properties、Android SharedPreferences .xml、player_prefs.preferences_pb（.pb）和音乐库 JSON。Android 酷狗账号槽位可同时选择 kugou_sessions.xml；账号凭据需主动勾选才会导入。',
            ),
            const SizedBox(height: 12),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: includeCredentials,
              onChanged: busy
                  ? null
                  : (value) => setState(() {
                      includeCredentials = value ?? false;
                      preview = null;
                    }),
              title: const Text('同时读取并导入账号凭据（可选）'),
              subtitle: const Text('凭据仅填补空缺账号，写入系统密钥环；保留已有账号。备份中的凭据可能已失效。'),
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: includeSettings,
              onChanged: busy
                  ? null
                  : (value) => setState(() {
                      includeSettings = value ?? false;
                      preview = null;
                    }),
              title: const Text('导入兼容的播放设置（可选）'),
              subtitle: const Text(
                '覆盖播放速度、音质、双行歌词和翻译偏好；支持 Android settings.xml 与 DataStore。不导入手机目录权限、悬浮窗权限或自动开始播放。',
              ),
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: includeQueue,
              onChanged: busy
                  ? null
                  : (value) => setState(() {
                      includeQueue = value ?? false;
                      preview = null;
                    }),
              title: const Text('导入旧版播放队列和进度（可选）'),
              subtitle: const Text(
                '读取 Android settings.xml / DataStore 中的播放状态；确认后停止当前播放、替换队列。不会自动播放或解析音源，点击播放后继续。',
              ),
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: includeEffects,
              onChanged: busy || widget.player.engine is! EffectsPlaybackEngine
                  ? null
                  : (value) => setState(() {
                      includeEffects = value ?? false;
                      preview = null;
                    }),
              title: const Text('导入旧版专业音效参数（可选）'),
              subtitle: const Text(
                '替换全部均衡器、低音、声场、混响、响度与母带参数；缺失值使用旧版默认值。保留当前专业模式开关，不导入 Android 系统音效引擎或自动启用音效。',
              ),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: busy ? null : select,
              icon: const Icon(Icons.file_open_outlined),
              label: const Text('选择备份并预览'),
            ),
            if (busy) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
            ],
            if (preview != null) ...[
              const SizedBox(height: 24),
              Text(
                '待合并：${preview!.decoded.tracks.length} 首歌曲，${preview!.decoded.favorites.length} 首收藏，${preview!.decoded.playlists.length} 个歌单。',
              ),
              if (preview!.skippedSongs > 0)
                Text('${preview!.skippedSongs} 条无法识别的音源记录将跳过。'),
              if (preview!.hasCredentials)
                const Text('含账号凭据，确认后只在本机保存；不会在界面显示凭据。'),
              if (preview!.playback.count > 0)
                Text('将覆盖 ${preview!.playback.count} 项兼容播放设置；其他设置保持不变。'),
              if (preview!.effects != null)
                const Text('将替换全部专业音效参数；当前专业模式开关保持不变，不会重新解析音源。'),
              if (preview!.queue != null) ...[
                Text(
                  '待恢复队列：${preview!.queue!.tracks.length} 项（含重复歌曲），第 ${preview!.queue!.session.index + 1} 项，进度 ${preview!.queue!.session.position.inSeconds} 秒。当前队列将被替换。',
                ),
                if (preview!.queue!.skipped > 0)
                  Text('队列中 ${preview!.queue!.skipped} 项无法识别的音源将跳过。'),
                if (preview!.queue!.selectionChanged)
                  const Text('原选中项无法识别，将选中后续可用项（末尾则回到首项），进度归零。'),
              ],
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: busy ? null : apply,
                icon: const Icon(Icons.merge),
                label: const Text('确认合并到本机'),
              ),
            ],
            if (message != null)
              Padding(
                padding: const EdgeInsets.only(top: 20),
                child: SelectableText(message!),
              ),
          ],
        ),
      ),
    ),
  );
}
