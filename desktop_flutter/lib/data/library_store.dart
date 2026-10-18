import 'package:shared_preferences/shared_preferences.dart';

abstract interface class LibraryStore {
  Future<String?> read();
  Future<void> write(String value);
}

class PreferencesLibraryStore implements LibraryStore {
  PreferencesLibraryStore(this.preferences);

  final SharedPreferencesAsync preferences;
  static const key = 'nekoplayer.flutter.library.v1';

  @override
  Future<String?> read() => preferences.getString(key);

  @override
  Future<void> write(String value) => preferences.setString(key, value);
}

class PreferencesDownloadStore implements LibraryStore {
  PreferencesDownloadStore(this.preferences);
  final SharedPreferencesAsync preferences;
  static const key = 'nekoplayer.flutter.downloads.v1';
  @override
  Future<String?> read() => preferences.getString(key);
  @override
  Future<void> write(String value) => preferences.setString(key, value);
}

class PreferencesCacheStore implements LibraryStore {
  PreferencesCacheStore(this.preferences);
  final SharedPreferencesAsync preferences;
  static const key = 'nekoplayer.flutter.cache.v1';
  @override
  Future<String?> read() => preferences.getString(key);
  @override
  Future<void> write(String value) => preferences.setString(key, value);
}
