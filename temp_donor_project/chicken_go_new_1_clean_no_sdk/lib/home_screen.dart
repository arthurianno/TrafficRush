import 'package:flutter/material.dart';

import 'audio_manager.dart';
import 'content_view.dart';
import 'settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  static const String _startAsset = 'assets/rush_eggs/start_button.png';
  static const String _letsGoAsset = 'assets/rush_eggs/lets_go.png';
  static const String _settingsAsset = 'assets/rush_eggs/settings_button.png';
  static const String _levelsAsset = 'assets/rush_eggs/levels_button.png';
  static const String _policyAsset = 'assets/rush_eggs/policy_button.png';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    AudioManager.instance.init();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.inactive) {
      AudioManager.instance.pause();
    } else if (state == AppLifecycleState.resumed) {
      AudioManager.instance.resume();
    }
  }

  void _openLevels() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const ContentView()),
    );
  }

  void _openSettings() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SettingsScreen()),
    );
  }

  void _openPrivacy() {
    showDialog<void>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('About'),
        content: const Text(
          'Standalone game export. Tracking, remote config, push, and webview flow removed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          const _RoadBackdrop(dimmed: true),
          SafeArea(
            child: OrientationBuilder(
              builder: (context, orientation) {
                final isLandscape = orientation == Orientation.landscape;
                return _HomeControls(
                  isLandscape: isLandscape,
                  settingsAsset: _settingsAsset,
                  levelsAsset: _levelsAsset,
                  policyAsset: _policyAsset,
                  letsGoAsset: _letsGoAsset,
                  startAsset: _startAsset,
                  onSettings: _openSettings,
                  onLevels: _openLevels,
                  onPolicy: _openPrivacy,
                  onStart: _openLevels,
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _HomeControls extends StatelessWidget {
  const _HomeControls({
    required this.isLandscape,
    required this.settingsAsset,
    required this.levelsAsset,
    required this.policyAsset,
    required this.letsGoAsset,
    required this.startAsset,
    required this.onSettings,
    required this.onLevels,
    required this.onPolicy,
    required this.onStart,
  });

  final bool isLandscape;
  final String settingsAsset;
  final String levelsAsset;
  final String policyAsset;
  final String letsGoAsset;
  final String startAsset;
  final VoidCallback onSettings;
  final VoidCallback onLevels;
  final VoidCallback onPolicy;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(26, 18, 26, 24),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          minHeight:
              MediaQuery.of(context).size.height -
              MediaQuery.of(context).padding.vertical -
              42,
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                _CircleAssetButton(asset: settingsAsset, onTap: onSettings),
                _CircleAssetButton(asset: levelsAsset, onTap: onLevels),
                _CircleAssetButton(asset: policyAsset, onTap: onPolicy),
              ],
            ),
            SizedBox(height: isLandscape ? 28 : 48),
            if (isLandscape)
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerRight,
                      child: Image.asset(
                        letsGoAsset,
                        width: 250,
                        fit: BoxFit.contain,
                      ),
                    ),
                  ),
                  const SizedBox(width: 24),
                  Expanded(
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: GestureDetector(
                        onTap: onStart,
                        child: Image.asset(
                          startAsset,
                          width: 280,
                          fit: BoxFit.contain,
                        ),
                      ),
                    ),
                  ),
                ],
              )
            else ...[
              Image.asset(letsGoAsset, width: 210, fit: BoxFit.contain),
              const SizedBox(height: 34),
              GestureDetector(
                onTap: onStart,
                child: Image.asset(startAsset, width: 250, fit: BoxFit.contain),
              ),
            ],
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }
}

class _CircleAssetButton extends StatelessWidget {
  const _CircleAssetButton({required this.asset, required this.onTap});

  final String asset;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 58,
        height: 58,
        decoration: const BoxDecoration(
          boxShadow: [
            BoxShadow(
              color: Color(0x80000000),
              blurRadius: 12,
              offset: Offset(0, 6),
            ),
          ],
        ),
        child: Image.asset(asset, fit: BoxFit.contain),
      ),
    );
  }
}

class _RoadBackdrop extends StatelessWidget {
  const _RoadBackdrop({required this.dimmed});

  final bool dimmed;

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
            if (dimmed) ColoredBox(color: Colors.black.withValues(alpha: 0.45)),
          ],
        );
      },
    );
  }
}
