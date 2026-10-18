import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../data/account_store.dart';
import '../data/legacy_import.dart';
import '../player_controller.dart';

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
      if (mounted) {
        setState(() {
          preview = null;
          message = '音乐库合并完成，导入 $accounts 份空缺账号凭据。原备份文件未修改；未访问云端。';
        });
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => message = librarySaved
              ? '音乐库已保存，但部分凭据保存失败。已保存的账号不会回滚；检查系统密钥环后可安全重试。'
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
