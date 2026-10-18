import 'package:flutter/material.dart';

ThemeData nekoTheme({
  Brightness brightness = Brightness.dark,
  double controlAlpha = 1,
  bool wallpaper = false,
}) {
  const darkColors = ColorScheme.dark(
    primary: Color(0xFFFF8C96),
    onPrimary: Color(0xFF351218),
    primaryContainer: Color(0xFF512830),
    secondary: Color(0xFFCAC2DC),
    secondaryContainer: Color(0xFF35303F),
    surface: Color(0xFF19191F),
    onSurface: Color(0xFFFFFFFF),
    onSurfaceVariant: Color(0xFFB8B9C5),
    outline: Color(0xFF858592),
    outlineVariant: Color(0xFF34343F),
    surfaceContainerLowest: Color(0xFF0D0D12),
    surfaceContainerLow: Color(0xFF17171D),
    surfaceContainer: Color(0xFF1E1E25),
    surfaceContainerHigh: Color(0xFF25252D),
    surfaceContainerHighest: Color(0xFF303039),
  );
  final colors = brightness == Brightness.dark
      ? darkColors
      : ColorScheme.fromSeed(
          seedColor: const Color(0xFFB84F61),
          brightness: Brightness.light,
        );
  return ThemeData(
    useMaterial3: true,
    colorScheme: colors,
    scaffoldBackgroundColor: wallpaper
        ? Colors.transparent
        : brightness == Brightness.dark
        ? const Color(0xFF111116)
        : const Color(0xFFFCF7F9),
    fontFamilyFallback: const [
      'Noto Sans CJK SC',
      'Microsoft YaHei',
      'sans-serif',
    ],
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      color: colors.surfaceContainerLow.withValues(alpha: controlAlpha),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: colors.surfaceContainerLow.withValues(alpha: controlAlpha),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(16),
        borderSide: BorderSide.none,
      ),
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: colors.surfaceContainerLow.withValues(
        alpha: controlAlpha,
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: colors.surfaceContainerLow.withValues(
        alpha: controlAlpha,
      ),
    ),
    dividerTheme: DividerThemeData(
      color: colors.outlineVariant,
      thickness: 1,
      space: 1,
    ),
  );
}
