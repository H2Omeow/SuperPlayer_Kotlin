import 'dart:async';

import 'package:flutter/material.dart';
import 'kugou_account_page.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../data/account_store.dart';
import '../data/native_music_catalog.dart';
import '../domain/online_music.dart';

class AccountsPanel extends StatefulWidget {
  const AccountsPanel({super.key, required this.catalog});
  final NativeMusicCatalog catalog;

  @override
  State<AccountsPanel> createState() => _AccountsPanelState();
}

class _AccountsPanelState extends State<AccountsPanel> {
  bool busy = false;
  String? message;
  LoginQr? qr;
  int generation = 0;
  AccountStore get accounts => widget.catalog.accounts;

  @override
  void dispose() {
    generation++;
    super.dispose();
  }

  Future<void> action(Future<void> Function() work) async {
    if (busy) return;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await work();
    } catch (failure) {
      if (mounted) {
        setState(() {
          message = failure is MusicSearchException
              ? failure.message
              : failure is FormatException
              ? failure.message
              : '账号操作失败，请检查系统密钥环、网络与凭据格式。';
        });
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> login(MusicProvider provider) async {
    final request = ++generation;
    await action(() async {
      final created = await widget.catalog.createLoginQr(provider);
      if (!mounted || request != generation) return;
      setState(() {
        qr = created;
        message =
            '请使用${provider.label}${provider == MusicProvider.kugou && created.platform == 1 ? '概念版' : ''}手机客户端扫码。';
      });
    });
    if (!mounted || request != generation || qr == null) return;
    final active = qr!;
    final expires = DateTime.now().add(const Duration(minutes: 3));
    while (mounted &&
        request == generation &&
        DateTime.now().isBefore(expires)) {
      await Future<void>.delayed(const Duration(seconds: 3));
      if (!mounted || request != generation) return;
      try {
        final result = await widget.catalog.checkLoginQr(
          active,
          accept: () => mounted && request == generation,
        );
        if (!mounted || request != generation) return;
        setState(() {
          message = switch (result) {
            LoginProgress.waiting => '等待扫码…',
            LoginProgress.scanned => '已扫码，请在手机上确认。',
            LoginProgress.expired => '二维码已过期，请重新生成。',
            LoginProgress.authorized => '登录成功，凭据已存入系统密钥环。',
          };
          if (result == LoginProgress.expired ||
              result == LoginProgress.authorized) {
            qr = null;
          }
        });
        if (qr == null) return;
      } catch (failure) {
        if (mounted && request == generation) {
          setState(() {
            message = failure is MusicSearchException
                ? failure.message
                : '扫码检查失败，请重新生成二维码。';
            qr = null;
          });
        }
        return;
      }
    }
    if (mounted && request == generation) {
      setState(() {
        qr = null;
        message = '二维码已过期，请重新生成。';
      });
    }
  }

  Future<void> importCookie(MusicProvider provider) async {
    generation++;
    setState(() => qr = null);
    final input = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('导入${provider.label} Cookie'),
        content: SizedBox(
          width: 420,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('仅导入你自己的账号凭据。凭据保存在系统密钥环，不上传至本站。'),
              const SizedBox(height: 12),
              TextField(
                controller: input,
                obscureText: true,
                autocorrect: false,
                enableSuggestions: false,
                decoration: const InputDecoration(
                  labelText: 'Cookie',
                  hintText: 'name=value; name=value',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, input.text),
            child: const Text('导入'),
          ),
        ],
      ),
    );
    input.dispose();
    if (!mounted || value == null) return;
    await action(() async {
      final cookies = AccountStore.parseCookies(value);
      if (provider == MusicProvider.netease) {
        if ((cookies['MUSIC_U'] ?? '').isEmpty) {
          throw const FormatException('缺少 MUSIC_U 登录凭据。');
        }
        await accounts.update(
          netease: {'deviceId': accounts.neteaseDevice, ...cookies},
          replace: true,
        );
      } else {
        if ((cookies['token'] ?? '').isEmpty ||
            (int.tryParse(cookies['userid'] ?? '') ?? 0) <= 0) {
          throw const FormatException('缺少有效的 token 或 userid。');
        }
        final device = {
          for (final entry in accounts.kugou().entries)
            if (entry.key.startsWith('KUGOU_API_') || entry.key == 'dfid')
              entry.key: entry.value,
        };
        await accounts.update(kugou: {...device, ...cookies}, replace: true);
      }
      if (mounted) setState(() => message = '凭据已导入；实际播放权限由音乐平台验证。');
    });
  }

  Future<void> logout(MusicProvider provider) async {
    generation++;
    setState(() => qr = null);
    await action(() async {
      if (provider == MusicProvider.netease) {
        await accounts.update(
          netease: {'deviceId': accounts.neteaseDevice},
          replace: true,
        );
      } else {
        await accounts.update(
          kugou: {
            for (final entry in accounts.kugou().entries)
              if (entry.key.startsWith('KUGOU_API_') || entry.key == 'dfid')
                entry.key: entry.value,
          },
          replace: true,
        );
      }
      if (mounted) setState(() => message = '已清除本机登录凭据。');
    });
  }

  Future<void> neteaseInfo() => action(() async {
    final original = accounts.netease['MUSIC_U'];
    if (!accounts.neteaseLoggedIn) {
      throw const MusicSearchException('请先登录网易云账号。');
    }
    final response = await widget.catalog.neteaseApi(
      '/api/nuser/account/get',
      {},
      weapi: true,
    );
    if (response['code'] != 200 || response['account'] is! Map) {
      throw const MusicSearchException('网易云未确认账号有效，请重新登录。');
    }
    if (!mounted || accounts.netease['MUSIC_U'] != original) return;
    final profile = response['profile'] is Map
        ? response['profile'] as Map
        : const {};
    final account = response['account'] as Map;
    setState(
      () => message =
          '${profile['nickname'] ?? '网易云用户'} · ID ${account['id'] ?? profile['userId'] ?? '未知'} · 会员类型 ${profile['vipType'] ?? 0}。实际可播音质由平台验证。',
    );
  });

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: accounts,
    builder: (context, _) => Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('音乐平台账号', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            const Text('账号仅在本机系统密钥环中保存；原版与概念版会话独立。'),
            if (!accounts.readable)
              const Padding(
                padding: EdgeInsets.only(top: 8),
                child: Text('系统凭据库不可用或数据损坏。已保护原数据，暂不保存账号；Linux 请解锁系统密钥环。'),
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              children: [
                for (var platform = 0; platform < 2; platform++)
                  ChoiceChip(
                    label: Text(platform == 0 ? '酷狗原版' : '酷狗概念版'),
                    selected: accounts.platform == platform,
                    onSelected: busy
                        ? null
                        : (_) {
                            generation++;
                            setState(() => qr = null);
                            unawaited(
                              action(() => accounts.update(platform: platform)),
                            );
                          },
                  ),
              ],
            ),
            for (final provider in const [
              MusicProvider.netease,
              MusicProvider.kugou,
            ])
              Padding(
                padding: const EdgeInsets.only(top: 12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${provider.label} · ${(provider == MusicProvider.netease ? accounts.neteaseLoggedIn : accounts.kugouLoggedIn()) ? '已保存登录凭据' : '未登录'}',
                    ),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        OutlinedButton(
                          onPressed: busy || !accounts.readable
                              ? null
                              : () => unawaited(login(provider)),
                          child: const Text('扫码登录'),
                        ),
                        TextButton(
                          onPressed: busy || !accounts.readable
                              ? null
                              : () => unawaited(importCookie(provider)),
                          child: const Text('导入 Cookie'),
                        ),
                        if (provider == MusicProvider.kugou)
                          TextButton(
                            onPressed: busy || !accounts.readable
                                ? null
                                : () => Navigator.of(context).push(
                                    MaterialPageRoute<void>(
                                      builder: (_) => KugouAccountPage(
                                        catalog: widget.catalog,
                                      ),
                                    ),
                                  ),
                            child: const Text('短信登录与权益'),
                          ),
                        if (provider == MusicProvider.netease)
                          TextButton(
                            onPressed: busy || !accounts.neteaseLoggedIn
                                ? null
                                : neteaseInfo,
                            child: const Text('验证账号与会员状态'),
                          ),
                        TextButton(
                          onPressed: busy || !accounts.readable
                              ? null
                              : () => unawaited(logout(provider)),
                          child: const Text('退出账号'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            if (busy) const LinearProgressIndicator(),
            if (qr != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: SizedBox(
                  width: 220,
                  height: 220,
                  child: ColoredBox(
                    color: Colors.white,
                    child: QrImageView(
                      data: qr!.uri.toString(),
                      padding: const EdgeInsets.all(12),
                    ),
                  ),
                ),
              ),
            if (message != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(message!),
              ),
          ],
        ),
      ),
    ),
  );
}
