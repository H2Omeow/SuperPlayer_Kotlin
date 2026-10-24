import 'package:flutter/material.dart';

import '../domain/library.dart';

class TimedLyricText extends StatelessWidget {
  const TimedLyricText({
    super.key,
    required this.line,
    required this.position,
    required this.active,
    this.showTranslation = true,
  });

  final LyricLine line;
  final Duration position;
  final bool active;
  final bool showTranslation;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final idle = colors.onSurfaceVariant;
    return Text.rich(
      TextSpan(
        children: [
          if (line.words.isEmpty || !active)
            TextSpan(text: line.text)
          else
            for (final word in line.words)
              TextSpan(
                text: word.text,
                style: TextStyle(
                  color: Color.lerp(
                    idle,
                    colors.primary,
                    word.progress(position),
                  ),
                ),
              ),
          if (showTranslation && line.translation.isNotEmpty)
            TextSpan(
              text: '\n${line.translation}',
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: idle),
            ),
        ],
      ),
      maxLines: 2,
      overflow: TextOverflow.ellipsis,
      style: Theme.of(context).textTheme.titleLarge?.copyWith(
        fontWeight: active ? FontWeight.bold : FontWeight.normal,
        color: active ? colors.primary : idle,
      ),
    );
  }
}
