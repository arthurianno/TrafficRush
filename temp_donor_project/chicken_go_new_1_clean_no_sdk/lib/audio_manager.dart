import 'package:audioplayers/audioplayers.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Simple singleton to manage looping background music and persisted volume.
///
/// Required pubspec.yaml:
///   dependencies:
///     audioplayers: ^6.0.0
///   assets:
///     - assets/rush_eggs/music.mp3
class AudioManager {
  AudioManager._();
  static final AudioManager instance = AudioManager._();

  static const String _volumeKey = 'music_volume';
  static const double defaultVolume = 0.8;

  final AudioPlayer _player = AudioPlayer();
  bool _initialized = false;
  double _volume = defaultVolume;

  double get volume => _volume;

  Future<void> init() async {
    if (_initialized) return;
    _initialized = true;

    final prefs = await SharedPreferences.getInstance();
    _volume = prefs.getDouble(_volumeKey) ?? defaultVolume;

    await _player.setReleaseMode(ReleaseMode.loop);
    await _player.setVolume(_volume);

    // Start music right away (home screen / game).
    await _player.play(AssetSource('rush_eggs/music.mp3'));
  }

  Future<void> setVolume(double v) async {
    _volume = v.clamp(0.0, 1.0);
    await _player.setVolume(_volume);
  }

  Future<void> saveVolume() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(_volumeKey, _volume);
  }

  Future<void> resetVolume() async {
    await setVolume(defaultVolume);
    await saveVolume();
  }

  Future<void> pause() => _player.pause();
  Future<void> resume() => _player.resume();
  Future<void> stop() => _player.stop();
}
