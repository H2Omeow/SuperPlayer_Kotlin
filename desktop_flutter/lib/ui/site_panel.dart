import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../site_controller.dart';
import '../data/source_gateway.dart';
import '../domain/online_music.dart';

class SitePanel extends StatefulWidget {
  const SitePanel({super.key, required this.controller, this.gateway});
  final SiteController controller;
  final SourceGateway? gateway;
  @override
  State<SitePanel> createState() => _SitePanelState();
}

class _SitePanelState extends State<SitePanel> {
  bool includeCredentials = false;
  bool readingUsage = false;

  Future<void> usage() async {
    if (readingUsage || widget.gateway == null) return;
    setState(() => readingUsage = true);
    final token = widget.controller.accounts.siteToken;
    try {
      final body = await widget.gateway!.request('usage', {});
      if (!mounted || token != widget.controller.accounts.siteToken) return;
      if (body['code'] != 200) throw const MusicSearchException('音源服务未返回有效配额。');
      int count(String key) => int.tryParse('${body[key]}') ?? 0;
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('惜缘音源使用配额'),
          content: Text(
            '日期：${body['date'] ?? '未知'}\n每日总额度：${count('limit')}\n已用：${count('used')}\n剩余：${count('remaining')}\n我的今日用量：${count('myToday')}',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('关闭'),
            ),
          ],
        ),
      );
    } catch (failure) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              failure is MusicSearchException
                  ? failure.message
                  : '配额读取失败，请检查本站登录状态。',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => readingUsage = false);
    }
  }

  Future<void> importToken() async {
    final input = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('导入本站登录令牌'),
        content: SizedBox(
          width: 420,
          child: TextField(
            controller: input,
            obscureText: true,
            autocorrect: false,
            enableSuggestions: false,
            decoration: const InputDecoration(
              labelText: '令牌或 nekoplayer://auth 回调链接',
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, input.text),
            child: const Text('验证并导入'),
          ),
        ],
      ),
    );
    input.dispose();
    if (mounted && value != null) await widget.controller.importToken(value);
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final controller = widget.controller;
      final accounts = controller.accounts;
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('本站账号与云同步', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text(
                controller.user == null
                    ? accounts.siteToken.isEmpty
                          ? '未登录本站'
                          : '已保存本站凭据，可验证账号'
                    : '你好，${controller.user!['nickname'] ?? controller.user!['username'] ?? '音乐爱好者'}',
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                children: [
                  OutlinedButton(
                    onPressed: controller.busy || !accounts.readable
                        ? null
                        : () => controller.login(
                            (uri) => launchUrl(
                              uri,
                              mode: LaunchMode.externalApplication,
                            ),
                          ),
                    child: const Text('浏览器登录'),
                  ),
                  TextButton(
                    onPressed: controller.busy || !accounts.readable
                        ? null
                        : importToken,
                    child: const Text('导入令牌'),
                  ),
                  if (accounts.siteToken.isNotEmpty) ...[
                    if (widget.gateway != null)
                      TextButton(
                        onPressed: controller.busy || readingUsage
                            ? null
                            : usage,
                        child: const Text('查看音源配额'),
                      ),
                    TextButton(
                      onPressed: controller.busy ? null : controller.refresh,
                      child: const Text('验证账号'),
                    ),
                    TextButton(
                      onPressed: controller.busy ? null : controller.logout,
                      child: const Text('退出本站'),
                    ),
                  ],
                ],
              ),
              CheckboxListTile(
                contentPadding: EdgeInsets.zero,
                controlAffinity: ListTileControlAffinity.leading,
                value: includeCredentials,
                onChanged: controller.busy
                    ? null
                    : (value) =>
                          setState(() => includeCredentials = value ?? false),
                title: const Text('同时同步音乐平台登录凭据'),
                subtitle: const Text(
                  '默认关闭。开启后，网易云与酷狗凭据将发送至本站服务器；本机已有账号优先，不自动覆盖。',
                ),
              ),
              const Text('手动合并收藏、历史与歌单；本地音频文件及其路径不会上传。登录不会自动同步。'),
              const SizedBox(height: 8),
              FilledButton.tonalIcon(
                onPressed: controller.busy || accounts.siteToken.isEmpty
                    ? null
                    : () => controller.sync(
                        includeCredentials: includeCredentials,
                      ),
                icon: const Icon(Icons.sync),
                label: const Text('合并并同步'),
              ),
              if (controller.busy) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(),
                TextButton(
                  onPressed: controller.cancelLogin,
                  child: const Text('取消等待浏览器登录'),
                ),
              ],
              if (controller.message != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(controller.message!),
                ),
            ],
          ),
        ),
      );
    },
  );
}
