import 'package:nekoplayer_desktop/data/library_store.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/playback/playback_engine.dart';

class MemoryStore implements LibraryStore {
  String? value;
  bool failWrite = false;
  int writes = 0;

  @override
  Future<String?> read() async => value;

  @override
  Future<void> write(String value) async {
    if (failWrite) throw StateError('disk full');
    writes++;
    this.value = value;
  }
}

class FakeEngine extends PlaybackEngine {
  @override
  bool playing = false;
  @override
  bool buffering = false;
  @override
  Duration position = Duration.zero;
  @override
  Duration duration = const Duration(minutes: 3);
  @override
  int index = 0;
  @override
  String? currentPath;
  @override
  String? error;
  double volume = 75;
  double rate = 1;
  RepeatMode repeat = RepeatMode.sequential;
  bool failOpen = false;

  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) async {
    if (failOpen) throw StateError('audio output unavailable');
    this.index = index;
    currentPath = tracks[index].path;
    playing = play;
    notifyListeners();
  }

  @override
  Future<void> replaceQueue(List<Track> tracks, int index) async {
    if (currentPath != tracks[index].path) {
      throw StateError('current track changed');
    }
    this.index = index;
    notifyListeners();
  }

  @override
  Future<void> clearQueue() async {
    playing = false;
    index = 0;
    currentPath = null;
    position = Duration.zero;
    notifyListeners();
  }

  @override
  Future<void> toggle() async {
    playing = !playing;
    notifyListeners();
  }

  @override
  Future<void> pause() async {
    playing = false;
    notifyListeners();
  }

  @override
  Future<void> next() async {}
  @override
  Future<void> previous() async {}
  @override
  Future<void> seek(Duration position) async {
    this.position = position;
    notifyListeners();
  }

  @override
  Future<void> setVolume(double value) async {
    volume = value;
  }

  @override
  Future<void> setRate(double value) async {
    rate = value;
  }

  @override
  Future<void> setRepeatMode(RepeatMode mode) async {
    repeat = mode;
  }
}
