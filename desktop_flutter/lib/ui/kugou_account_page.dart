import 'dart:async';

import 'package:flutter/material.dart';

import '../data/native_music_catalog.dart';
import '../domain/online_music.dart';

class KugouAccountPage extends StatefulWidget {
  const KugouAccountPage({super.key, required this.catalog});
  final NativeMusicCatalog catalog;
  @override
  State<KugouAccountPage> createState() => _KugouAccountPageState();
}

class _KugouAccountPageState extends State<KugouAccountPage> {
  final phone = TextEditingController();
  final code = TextEditingController();
  bool busy = false;
  String? message;
  String? nickname;
  String? vip;
  List<KugouLoginChoice> choices = [];
  DateTime? sentAt;
  Timer? timer;
  int generation = 0;
  late int platform;
  late String? token;

  @override
  void initState() {
    super.initState();
    platform = widget.catalog.accounts.platform;
    token = widget.catalog.accounts.kugou()['token'];
    widget.catalog.accounts.addListener(accountChanged);
  }

  void accountChanged() {
    final accounts = widget.catalog.accounts;
    if (platform == accounts.platform && token == accounts.kugou()['token']) {
      return;
    }
    generation++;
    platform = accounts.platform;
    token = accounts.kugou()['token'];
    if (mounted) {
      setState(() {
        nickname = null;
        vip = null;
        choices = [];
      });
    }
  }

  Future<void> action(Future<void> Function(int request) work) async {
    if (busy) return;
    final request = ++generation;
    setState(() {
      busy = true;
      message = null;
    });
    try {
      await work(request);
    } catch (failure) {
      if (mounted && request == generation) {
        setState(() {
          if (failure is KugouAccountSelection) {
            choices = failure.accounts;
            message = '该手机号关联多个账号，请选择要登录的账号。';
          } else {
            message = failure is MusicSearchException
                ? failure.message
                : '账号操作失败，请检查网络、设备验证和系统密钥环。';
          }
        });
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> send() => action((request) async {
    await widget.catalog.sendKugouCode(phone.text.trim());
    if (!mounted || request != generation) return;
    setState(() {
      sentAt = DateTime.now();
      message = '平台已确认验证码发送，请检查短信。';
    });
    timer?.cancel();
    timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
      if (remaining == 0) timer?.cancel();
    });
  });

  int get remaining => sentAt == null
      ? 0
      : (60 - DateTime.now().difference(sentAt!).inSeconds).clamp(0, 60);

  Future<void> login([String? userId]) => action((request) async {
    await widget.catalog.loginKugouMobile(
      phone.text.trim(),
      code.text.trim(),
      userId: userId,
      accept: () => mounted && request == generation,
    );
    if (mounted) {
      setState(() {
        choices = [];
        code.clear();
        message = '已保存平台确认的登录凭据。';
      });
    }
  });

  Future<void> info() => action((request) async {
    final user = await widget.catalog.kugouAccountInfo();
    final membership = await widget.catalog.kugouAccountInfo(vip: true);
    if (!mounted || request != generation) return;
    final rawEnd =
        int.tryParse(
          '${membership['end_time'] ?? membership['vip_end_time'] ?? 0}',
        ) ??
        0;
    String expiry = '未提供有效期';
    if (rawEnd > 0 && rawEnd < 9999999999999) {
      final end = DateTime.fromMillisecondsSinceEpoch(
        rawEnd > 100000000000 ? rawEnd : rawEnd * 1000,
      ).toLocal();
      expiry =
          '${end.year}-${end.month.toString().padLeft(2, '0')}-${end.day.toString().padLeft(2, '0')}';
    }
    setState(() {
      nickname =
          '${user['nickname'] ?? user['nick_name'] ?? user['username'] ?? '酷狗用户'}';
      vip =
          '会员类型 ${membership['vip_type'] ?? membership['vip_level'] ?? 0} · $expiry';
      message = '账号信息已更新；可播音质仍由播放接口验证。';
    });
  });

  Future<void> claim() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('领取概念版听歌权益？'),
        content: const Text('将使用当前概念版账号向官方接口发送一次领取请求。不会自动重复领取，也不保证账号满足领取条件。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认领取'),
          ),
        ],
      ),
    );
    if (!mounted || confirm != true) return;
    await action((request) async {
      await widget.catalog.claimKugouDailyVip();
      if (mounted && request == generation) {
        setState(() => message = '平台已确认领取请求成功；具体权益请在官方客户端检查。');
      }
    });
  }

  @override
  void dispose() {
    generation++;
    timer?.cancel();
    widget.catalog.accounts.removeListener(accountChanged);
    phone.dispose();
    code.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('酷狗账号与权益')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760),
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              platform == 0 ? '当前账号槽位：酷狗原版' : '当前账号槽位：酷狗概念版',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 12),
            const Text('短信发送需要平台设备验证。只有点击发送时才联系官方短信接口；不会自动发送或重试。'),
            const SizedBox(height: 16),
            TextField(
              controller: phone,
              enabled: !busy,
              keyboardType: TextInputType.phone,
              maxLength: 11,
              onChanged: (_) => setState(() => choices = []),
              decoration: const InputDecoration(labelText: '大陆手机号'),
            ),
            OutlinedButton(
              onPressed: busy || remaining > 0 ? null : send,
              child: Text(remaining > 0 ? '$remaining 秒后可重发' : '发送短信验证码'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: code,
              enabled: !busy,
              obscureText: true,
              keyboardType: TextInputType.number,
              maxLength: 8,
              decoration: const InputDecoration(labelText: '短信验证码'),
            ),
            FilledButton(
              onPressed: busy ? null : login,
              child: const Text('使用验证码登录'),
            ),
            for (final choice in choices)
              ListTile(
                title: Text(
                  choice.name.isEmpty ? '账号 ${choice.id}' : choice.name,
                ),
                subtitle: Text('ID ${choice.id}'),
                trailing: const Icon(Icons.login),
                onTap: busy ? null : () => login(choice.id),
              ),
            const SizedBox(height: 24),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  onPressed: busy || !widget.catalog.accounts.kugouLoggedIn()
                      ? null
                      : info,
                  child: const Text('读取账号与会员状态'),
                ),
                OutlinedButton(
                  onPressed: busy || !widget.catalog.accounts.kugouLoggedIn()
                      ? null
                      : () => action((request) async {
                          await widget.catalog.refreshKugouToken(
                            accept: () => mounted && request == generation,
                          );
                          if (mounted) {
                            setState(() => message = '登录凭据已通过平台确认并刷新。');
                          }
                        }),
                  child: const Text('刷新登录凭据'),
                ),
                if (platform == 1)
                  OutlinedButton(
                    onPressed: busy || !widget.catalog.accounts.kugouLoggedIn()
                        ? null
                        : claim,
                    child: const Text('手动领取每日听歌权益'),
                  ),
              ],
            ),
            if (nickname != null)
              Padding(
                padding: const EdgeInsets.only(top: 20),
                child: Text('$nickname\n$vip'),
              ),
            if (busy)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: LinearProgressIndicator(),
              ),
            if (message != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(message!),
              ),
          ],
        ),
      ),
    ),
  );
}
