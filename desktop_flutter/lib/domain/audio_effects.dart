import 'mastering_presets.dart';

class AudioEffects {
  const AudioEffects({
    this.enabled = false,
    this.eq = const [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    this.bass = 0,
    this.width = 0,
    this.wet = 0,
    this.room = 50,
    this.damping = 30,
    this.loudness = 0,
    this.mastering = 0,
    this.mix = 100,
  });
  final bool enabled;
  final List<double> eq;
  final double bass;
  final double width;
  final double wet;
  final double room;
  final double damping;
  final double loudness;
  final int mastering;
  final double mix;
  static const frequencies = [
    32,
    64,
    125,
    250,
    500,
    1000,
    2000,
    4000,
    8000,
    16000,
  ];
  static const presets = <String, List<double>>{
    '流行': [0, 2, 3, 4, 3, 2, 1, 0, 0, 0],
    '摇滚': [5, 4, 2, 0, -1, -1, 0, 2, 4, 5],
    '古典': [0, 0, 0, 0, 0, 0, -2, -3, -4, -5],
    '人声': [0, -2, -1, 1, 3, 3, 2, 1, 0, 0],
    '低音炮': [8, 6, 3, 1, -1, -1, 0, 1, 2, 3],
    '次元回响': [3, 2, 1, 0, -1, -1, 0, 1, 2, 3],
    '星海幻梦': [2, 3, 2, 1, 0, 0, 1, 2, 3, 2],
    '初音絮语': [4, 3, 2, 1, 0, -1, 0, 1, 2, 3],
    '樱瓣环绕': [4, 3, 2, 0, -2, -2, 0, 2, 4, 5],
    '神谕调音': [2, 4, 2, -1, -2, 0, 2, 4, 5, 4],
    '次元穿透': [5, 4, 2, -1, -3, -3, -1, 2, 4, 6],
    '幻灵近耳': [5, 4, 2, 1, 0, -2, 1, 3, 5, 4],
    '月华音乐厅': [1, 2, 3, 3, 2, 2, 1, 1, 0, -1],
    '琉璃现场': [0, 1, 2, 3, 3, 2, 1, 1, 0, -1],
    '深海低语': [10, 8, 4, 2, 0, -2, -2, -1, 0, 1],
    '心跳节拍': [8, 6, 3, 1, -1, -1, 1, 3, 6, 8],
    '次元共鸣': [3, 4, 5, 4, 2, 1, 0, 2, 4, 5],
    '雷电脉冲': [9, 7, 4, 1, -1, -1, 0, 2, 5, 7],
    '月光守护': [-5, -3, -1, 0, 1, 2, 1, 0, -1, -2],
    '龙吟低炮': [12, 10, 6, 3, 1, -1, -2, -2, 0, 2],
    'AI调音大师': [2, 3, 1, -1, -1, 0, 1, 2, 3, 2],
  };

  static double clamp(double value, double minimum, double maximum) =>
      value.isFinite ? value.clamp(minimum, maximum) : 0;
  List<double> get values => [
    for (var index = 0; index < 10; index++)
      clamp(index < eq.length ? eq[index] : 0, -15, 15),
    for (final value in [bass, width, wet, room, damping, loudness])
      clamp(value, 0, 100),
    MasteringPreset.find(mastering)?.id.toDouble() ?? 0,
    clamp(mix, 0, 100),
  ];
  AudioEffects copyWith({
    bool? enabled,
    List<double>? eq,
    double? bass,
    double? width,
    double? wet,
    double? room,
    double? damping,
    double? loudness,
    int? mastering,
    double? mix,
  }) => AudioEffects(
    enabled: enabled ?? this.enabled,
    eq: eq ?? this.eq,
    bass: bass ?? this.bass,
    width: width ?? this.width,
    wet: wet ?? this.wet,
    room: room ?? this.room,
    damping: damping ?? this.damping,
    loudness: loudness ?? this.loudness,
    mastering: mastering ?? this.mastering,
    mix: mix ?? this.mix,
  );
  Map<String, dynamic> toJson() => {
    'version': 1,
    'enabled': enabled,
    'values': values,
  };
  factory AudioEffects.fromJson(Map<String, dynamic> body) {
    final values = body['values'];
    if (body['version'] != 1 ||
        values is! List ||
        values.length != 18 ||
        values.any((value) => value is! num || !value.isFinite)) {
      throw const FormatException('Invalid effects settings');
    }
    final numbers = values
        .cast<num>()
        .map((value) => value.toDouble())
        .toList();
    return AudioEffects(
      enabled: body['enabled'] == true,
      eq: numbers.take(10).toList(),
      bass: numbers[10],
      width: numbers[11],
      wet: numbers[12],
      room: numbers[13],
      damping: numbers[14],
      loudness: numbers[15],
      mastering: numbers[16].toInt(),
      mix: numbers[17],
    );
  }
}
