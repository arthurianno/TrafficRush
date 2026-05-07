import 'package:flutter/material.dart';

import 'audio_manager.dart';
import 'app_road_loader.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const String _widePanelAsset = 'assets/rush_eggs/wide_panel.png';
  static const String _framePanelAsset = 'assets/rush_eggs/frame_panel.png';
  static const String _soundOnAsset = 'assets/rush_eggs/sound_on_button.png';
  static const String _soundOffAsset = 'assets/rush_eggs/sound_off_button.png';
  static const String _homeAsset = 'assets/rush_eggs/home_button.png';

  bool _loading = true;
  bool _soundEnabled = true;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    await AudioManager.instance.init();
    if (!mounted) return;
    setState(() {
      _soundEnabled = AudioManager.instance.volume > 0.01;
      _loading = false;
    });
  }

  Future<void> _setSound(bool enabled) async {
    await AudioManager.instance.setVolume(
      enabled ? AudioManager.defaultVolume : 0,
    );
    await AudioManager.instance.saveVolume();
    if (!mounted) return;
    setState(() {
      _soundEnabled = enabled;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          const _SettingsRoadBackdrop(),
          if (_loading)
            const AppRoadLoader()
          else
            SafeArea(
              child: OrientationBuilder(
                builder: (context, orientation) {
                  final isLandscape = orientation == Orientation.landscape;
                  return SingleChildScrollView(
                    padding: const EdgeInsets.all(20),
                    child: ConstrainedBox(
                      constraints: BoxConstraints(
                        minHeight:
                            MediaQuery.of(context).size.height -
                            MediaQuery.of(context).padding.vertical -
                            40,
                      ),
                      child: Center(
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxWidth: isLandscape ? 900 : 540,
                          ),
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              _TitleBar(
                                text: 'Settings',
                                asset: _widePanelAsset,
                              ),
                              const SizedBox(height: 28),
                              Stack(
                                alignment: Alignment.center,
                                children: [
                                  SizedBox(
                                    width: isLandscape ? 760 : 440,
                                    child: Image.asset(
                                      _framePanelAsset,
                                      fit: BoxFit.fill,
                                    ),
                                  ),
                                  Padding(
                                    padding: EdgeInsets.symmetric(
                                      horizontal: isLandscape ? 90 : 54,
                                      vertical: isLandscape ? 88 : 70,
                                    ),
                                    child: Row(
                                      mainAxisAlignment:
                                          MainAxisAlignment.spaceEvenly,
                                      children: [
                                        _SettingsIconButton(
                                          asset: _soundOnAsset,
                                          selected: _soundEnabled,
                                          onTap: () => _setSound(true),
                                        ),
                                        _SettingsIconButton(
                                          asset: _soundOffAsset,
                                          selected: !_soundEnabled,
                                          onTap: () => _setSound(false),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 26),
                              GestureDetector(
                                onTap: () => Navigator.of(context).pop(),
                                child: Image.asset(
                                  _homeAsset,
                                  width: 70,
                                  fit: BoxFit.contain,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _TitleBar extends StatelessWidget {
  const _TitleBar({required this.text, required this.asset});

  final String text;
  final String asset;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 84,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Image.asset(asset, fit: BoxFit.contain),
          Text(
            text,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 24,
              fontWeight: FontWeight.w900,
              shadows: [
                Shadow(
                  color: Color(0x99000000),
                  blurRadius: 6,
                  offset: Offset(0, 2),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsIconButton extends StatelessWidget {
  const _SettingsIconButton({
    required this.asset,
    required this.selected,
    required this.onTap,
  });

  final String asset;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedScale(
        scale: selected ? 1.06 : 1,
        duration: const Duration(milliseconds: 160),
        child: Container(
          width: 86,
          height: 86,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(44),
            boxShadow: [
              BoxShadow(
                color: selected
                    ? const Color(0x88FFB347)
                    : const Color(0x55000000),
                blurRadius: selected ? 18 : 10,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: Opacity(
            opacity: selected ? 1 : 0.72,
            child: Image.asset(asset, fit: BoxFit.contain),
          ),
        ),
      ),
    );
  }
}

class _SettingsRoadBackdrop extends StatelessWidget {
  const _SettingsRoadBackdrop();

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final borderWidth = constraints.maxWidth * 0.03;
        return Stack(
          fit: StackFit.expand,
          children: [
            Image.asset('assets/rush_eggs/road.png', fit: BoxFit.fill),
            Positioned(
              top: 0,
              bottom: 0,
              left: 0,
              width: borderWidth,
              child: Image.asset(
                'assets/rush_eggs/road_border.png',
                fit: BoxFit.fill,
              ),
            ),
            Positioned(
              top: 0,
              bottom: 0,
              right: 0,
              width: borderWidth,
              child: Transform.flip(
                flipX: true,
                child: Image.asset(
                  'assets/rush_eggs/road_border.png',
                  fit: BoxFit.fill,
                ),
              ),
            ),
            ColoredBox(color: Colors.black.withValues(alpha: 0.45)),
          ],
        );
      },
    );
  }
}
