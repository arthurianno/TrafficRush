import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'app_road_loader.dart';
import 'settings_screen.dart';

class ContentView extends StatefulWidget {
  const ContentView({super.key});

  @override
  State<ContentView> createState() => _ContentViewState();
}

class _ContentViewState extends State<ContentView> {
  static const int _totalLevels = 15;
  static const int _laneCount = 5;
  static const String _scoreKey = 'rush_eggs_score';
  static const String _completedKey = 'rush_eggs_completed_levels';

  static const String _framePanelAsset = 'assets/rush_eggs/frame_panel.png';
  static const String _widePanelAsset = 'assets/rush_eggs/wide_panel.png';
  static const String _homeButtonAsset = 'assets/rush_eggs/home_button.png';
  static const String _settingsButtonAsset =
      'assets/rush_eggs/settings_button.png';
  static const String _restartButtonAsset =
      'assets/rush_eggs/restart_button.png';
  static const String _levelLockedAsset = 'assets/rush_eggs/level_locked.png';
  static const String _levelOpenAsset = 'assets/rush_eggs/level_open.png';
  static const String _starActiveAsset = 'assets/rush_eggs/star_active.png';
  static const String _starInactiveAsset = 'assets/rush_eggs/star_inactive.png';
  static const String _looooseAsset = 'assets/rush_eggs/loooose.png';
  static const String _scoreLabelAsset = 'assets/rush_eggs/score_label.png';

  final math.Random _random = math.Random();
  Timer? _tickTimer;
  Timer? _spawnTimer;

  int _selectedLevel = 1;
  int _score = 0;
  int _carLane = 2;
  int _health = 3;
  int _collectedItems = 0;
  bool _isPlaying = false;
  bool _showResult = false;
  bool _didWin = false;
  bool _progressLoaded = false;
  Set<int> _completedLevels = <int>{};
  List<_FallingItem> _items = <_FallingItem>[];

  @override
  void initState() {
    super.initState();
    _loadProgress();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    for (final asset in <String>[
      _framePanelAsset,
      _widePanelAsset,
      _homeButtonAsset,
      _settingsButtonAsset,
      _restartButtonAsset,
      _levelLockedAsset,
      _levelOpenAsset,
      _starActiveAsset,
      _starInactiveAsset,
      _looooseAsset,
      _scoreLabelAsset,
      for (final item in _gameItemTemplates) item.asset,
    ]) {
      precacheImage(AssetImage(asset), context);
    }
  }

  @override
  void dispose() {
    _stopGameTimers();
    super.dispose();
  }

  int get _goalForSelectedLevel => _selectedLevel * 5;

  Future<void> _loadProgress() async {
    final prefs = await SharedPreferences.getInstance();
    final completedRaw = prefs.getStringList(_completedKey) ?? <String>[];
    final completedLevels = completedRaw
        .map(int.tryParse)
        .whereType<int>()
        .where((level) => level >= 1 && level <= _totalLevels)
        .toSet();

    final nextLevel = completedLevels.isEmpty
        ? 1
        : math.min(completedLevels.reduce(math.max) + 1, _totalLevels);

    if (!mounted) return;

    setState(() {
      _score = prefs.getInt(_scoreKey) ?? 0;
      _completedLevels = completedLevels;
      _selectedLevel = nextLevel;
      _progressLoaded = true;
    });
  }

  Future<void> _saveProgress() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(_scoreKey, _score);
    await prefs.setStringList(
      _completedKey,
      (_completedLevels.toList()..sort()).map((value) => '$value').toList(),
    );
  }

  bool _isLevelUnlocked(int level) {
    if (level <= 1) return true;
    return _completedLevels.contains(level - 1);
  }

  void _startLevel({int? level}) {
    _stopGameTimers();
    setState(() {
      if (level != null) {
        _selectedLevel = level;
      }
      _isPlaying = true;
      _showResult = false;
      _didWin = false;
      _carLane = 2;
      _health = 3;
      _collectedItems = 0;
      _items = <_FallingItem>[];
    });

    _tickTimer = Timer.periodic(const Duration(milliseconds: 16), (_) {
      _advanceGame(1 / 60);
    });
    _scheduleNextSpawn();
  }

  void _scheduleNextSpawn() {
    _spawnTimer?.cancel();
    if (!_isPlaying) return;

    _spawnTimer = Timer(Duration(milliseconds: 820 + _random.nextInt(680)), () {
      if (!_isPlaying || !mounted) return;
      _spawnItem();
      _scheduleNextSpawn();
    });
  }

  void _spawnItem() {
    final template =
        _gameItemTemplates[_random.nextInt(_gameItemTemplates.length)];
    final lane = _random.nextInt(_laneCount);
    final speed = 0.34 + (_selectedLevel * 0.05) + _random.nextDouble() * 0.06;

    setState(() {
      _items = List<_FallingItem>.from(_items)
        ..add(
          _FallingItem(
            lane: lane,
            y: -0.16,
            speed: speed,
            size: template.size,
            score: template.score,
            asset: template.asset,
          ),
        );
    });
  }

  void _advanceGame(double dt) {
    if (!_isPlaying || !mounted) return;

    var nextScore = _score;
    var nextHealth = _health;
    var nextCollected = _collectedItems;
    var finish = false;
    var win = false;

    final nextItems = <_FallingItem>[];
    for (final item in _items) {
      final moved = item.copyWith(y: item.y + item.speed * dt);
      final hit = moved.lane == _carLane && moved.y >= 0.79 && moved.y <= 0.93;

      if (hit) {
        nextScore += moved.score;
        if (moved.score > 0) {
          nextCollected += 1;
          if (nextCollected >= _goalForSelectedLevel) {
            finish = true;
            win = true;
          }
        } else {
          nextHealth -= 1;
          if (nextHealth <= 0) {
            finish = true;
            win = false;
          }
        }
        continue;
      }

      if (moved.y <= 1.16) {
        nextItems.add(moved);
      }
    }

    setState(() {
      _score = nextScore;
      _health = nextHealth;
      _collectedItems = nextCollected;
      _items = nextItems;
    });

    if (finish) {
      _finishLevel(win: win);
    }
  }

  Future<void> _finishLevel({required bool win}) async {
    _stopGameTimers();
    if (win) {
      _completedLevels = Set<int>.from(_completedLevels)..add(_selectedLevel);
    }

    if (!mounted) return;

    setState(() {
      _isPlaying = false;
      _didWin = win;
      _showResult = true;
    });

    await _saveProgress();
  }

  void _stopGameTimers() {
    _tickTimer?.cancel();
    _tickTimer = null;
    _spawnTimer?.cancel();
    _spawnTimer = null;
  }

  void _moveCar(int delta) {
    if (!_isPlaying) return;
    setState(() {
      _carLane = (_carLane + delta).clamp(0, _laneCount - 1);
    });
  }

  void _moveCarToLane(int lane) {
    if (!_isPlaying) return;
    setState(() {
      _carLane = lane.clamp(0, _laneCount - 1);
    });
  }

  void _goHome() {
    _stopGameTimers();
    Navigator.of(context).pop();
  }

  void _backToLevels() {
    _stopGameTimers();
    setState(() {
      _isPlaying = false;
      _showResult = false;
      _didWin = false;
      _items = <_FallingItem>[];
      _health = 3;
      _collectedItems = 0;
      _carLane = 2;
    });
  }

  void _openSettings() {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const SettingsScreen()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          _isPlaying
              ? const _FullGameRoadBackdrop()
              : _SharedRoadBackdrop(dimmed: true),
          if (!_progressLoaded)
            const AppRoadLoader()
          else
            SafeArea(
              child: Stack(
                children: [
                  Positioned.fill(
                    child: _isPlaying
                        ? _buildGameView(context)
                        : _buildLevelsView(context),
                  ),
                  if (_showResult) _buildResultOverlay(context),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildLevelsView(BuildContext context) {
    return OrientationBuilder(
      builder: (context, orientation) {
        final isLandscape = orientation == Orientation.landscape;

        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(18, 16, 18, 18),
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minHeight:
                  MediaQuery.of(context).size.height -
                  MediaQuery.of(context).padding.vertical -
                  34,
            ),
            child: Center(
              child: ConstrainedBox(
                constraints: BoxConstraints(maxWidth: isLandscape ? 980 : 520),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    _WideTitleBar(text: 'Levels'),
                    const SizedBox(height: 18),
                    Builder(
                      builder: (context) {
                        final panelWidth = isLandscape ? 470.0 : 410.0;
                        final panelHeight = panelWidth / (764 / 1104);
                        return SizedBox(
                          width: panelWidth,
                          height: panelHeight,
                          child: Stack(
                            alignment: Alignment.center,
                            children: [
                              Positioned.fill(
                                child: Image.asset(
                                  _framePanelAsset,
                                  fit: BoxFit.fill,
                                ),
                              ),
                              Padding(
                                padding: EdgeInsets.symmetric(
                                  horizontal: isLandscape ? 52 : 36,
                                  vertical: isLandscape ? 58 : 46,
                                ),
                                child: Scrollbar(
                                  child: GridView.builder(
                                    primary: false,
                                    padding: const EdgeInsets.only(right: 6),
                                    itemCount: _totalLevels,
                                    gridDelegate:
                                        SliverGridDelegateWithFixedCrossAxisCount(
                                          crossAxisCount: isLandscape ? 5 : 3,
                                          crossAxisSpacing: 18,
                                          mainAxisSpacing: 18,
                                          childAspectRatio: 0.88,
                                        ),
                                    itemBuilder: (context, index) {
                                      final level = index + 1;
                                      final unlocked = _isLevelUnlocked(level);
                                      return GestureDetector(
                                        onTap: unlocked
                                            ? () => _startLevel(level: level)
                                            : null,
                                        child: Stack(
                                          alignment: Alignment.center,
                                          children: [
                                            Positioned.fill(
                                              child: Image.asset(
                                                unlocked
                                                    ? _levelOpenAsset
                                                    : _levelLockedAsset,
                                                fit: BoxFit.contain,
                                              ),
                                            ),
                                            if (_selectedLevel == level)
                                              Container(
                                                margin: const EdgeInsets.all(2),
                                                decoration: BoxDecoration(
                                                  borderRadius:
                                                      BorderRadius.circular(18),
                                                  border: Border.all(
                                                    color: const Color(
                                                      0xFFFFD86A,
                                                    ),
                                                    width: 2,
                                                  ),
                                                ),
                                              ),
                                            Text(
                                              '$level',
                                              style: TextStyle(
                                                color: unlocked
                                                    ? const Color(0xFF6D3C00)
                                                    : Colors.white60,
                                                fontSize: 24,
                                                fontWeight: FontWeight.w900,
                                              ),
                                            ),
                                          ],
                                        ),
                                      );
                                    },
                                  ),
                                ),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                    const SizedBox(height: 16),
                    GestureDetector(
                      onTap: _goHome,
                      child: Image.asset(
                        _homeButtonAsset,
                        width: 74,
                        fit: BoxFit.contain,
                      ),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      'Level $_selectedLevel  •  target $_goalForSelectedLevel',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    GestureDetector(
                      onTap: _startLevel,
                      child: SizedBox(
                        height: 84,
                        child: Stack(
                          alignment: Alignment.center,
                          children: [
                            Image.asset(_widePanelAsset, fit: BoxFit.contain),
                            const Text(
                              'Play',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 22,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildGameView(BuildContext context) {
    return OrientationBuilder(
      builder: (context, orientation) {
        final isLandscape = orientation == Orientation.landscape;
        return LayoutBuilder(
          builder: (context, constraints) {
            final roadWidth = isLandscape
                ? constraints.maxWidth
                : constraints.maxWidth;
            final roadHeight = isLandscape
                ? constraints.maxHeight * 0.72
                : constraints.maxHeight * 0.68;

            final road = _RoadGameView(
              roadWidth: roadWidth,
              roadHeight: roadHeight,
              items: _items,
              carLane: _carLane,
              laneCount: _laneCount,
              onTapLane: _moveCarToLane,
            );

            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
                  child: Row(
                    children: [
                      _RoundButtonImage(
                        asset: _homeButtonAsset,
                        onTap: _backToLevels,
                      ),
                      const Spacer(),
                      Column(
                        children: [
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Image.asset(
                                _scoreLabelAsset,
                                width: 88,
                                fit: BoxFit.contain,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                '$_score',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              for (int i = 0; i < 3; i++) ...[
                                Image.asset(
                                  i < _health
                                      ? _starActiveAsset
                                      : _starInactiveAsset,
                                  width: 24,
                                  height: 24,
                                ),
                                if (i < 2) const SizedBox(width: 4),
                              ],
                            ],
                          ),
                        ],
                      ),
                      const Spacer(),
                      _RoundButtonImage(
                        asset: _settingsButtonAsset,
                        onTap: _openSettings,
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: roadWidth,
                        child: Align(
                          alignment: Alignment.center,
                          child: Text(
                            '$_collectedItems / $_goalForSelectedLevel',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(height: 8),
                      road,
                      const SizedBox(height: 12),
                      if (isLandscape)
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            for (final item in _gameItemTemplates) ...[
                              Image.asset(item.asset, width: 40, height: 40),
                              const SizedBox(width: 8),
                            ],
                          ],
                        )
                      else
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            _ArrowButton(
                              icon: Icons.arrow_left_rounded,
                              onTap: () => _moveCar(-1),
                            ),
                            const SizedBox(width: 18),
                            _ArrowButton(
                              icon: Icons.arrow_right_rounded,
                              onTap: () => _moveCar(1),
                            ),
                          ],
                        ),
                    ],
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _buildResultOverlay(BuildContext context) {
    return Positioned.fill(
      child: ColoredBox(
        color: Colors.black.withValues(alpha: 0.55),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 28),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Image.asset(_framePanelAsset, fit: BoxFit.contain),
                  Padding(
                    padding: const EdgeInsets.fromLTRB(44, 54, 44, 46),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (_didWin)
                          const Text(
                            'Level Clear',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 28,
                              fontWeight: FontWeight.w900,
                            ),
                          )
                        else
                          Image.asset(
                            _looooseAsset,
                            width: 220,
                            fit: BoxFit.contain,
                          ),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Image.asset(
                              _scoreLabelAsset,
                              width: 96,
                              fit: BoxFit.contain,
                            ),
                            const SizedBox(width: 6),
                            Text(
                              '$_score',
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 22,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 22),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            _RoundButtonImage(
                              asset: _homeButtonAsset,
                              onTap: _backToLevels,
                              size: 74,
                            ),
                            const SizedBox(width: 18),
                            _RoundButtonImage(
                              asset: _restartButtonAsset,
                              onTap: () {
                                setState(() {
                                  _showResult = false;
                                });
                                _startLevel();
                              },
                              size: 74,
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _WideTitleBar extends StatelessWidget {
  const _WideTitleBar({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 84,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Image.asset('assets/rush_eggs/wide_panel.png', fit: BoxFit.contain),
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

class _RoundButtonImage extends StatelessWidget {
  const _RoundButtonImage({
    required this.asset,
    required this.onTap,
    this.size = 58,
  });

  final String asset;
  final VoidCallback onTap;
  final double size;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: size,
        height: size,
        child: Image.asset(asset, fit: BoxFit.contain),
      ),
    );
  }
}

class _ArrowButton extends StatelessWidget {
  const _ArrowButton({required this.icon, required this.onTap});

  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 76,
        height: 54,
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: Colors.white.withValues(alpha: 0.16)),
        ),
        child: Icon(icon, color: Colors.white, size: 34),
      ),
    );
  }
}

class _RoadGameView extends StatelessWidget {
  const _RoadGameView({
    required this.roadWidth,
    required this.roadHeight,
    required this.items,
    required this.carLane,
    required this.laneCount,
    required this.onTapLane,
  });

  final double roadWidth;
  final double roadHeight;
  final List<_FallingItem> items;
  final int carLane;
  final int laneCount;
  final ValueChanged<int> onTapLane;

  @override
  Widget build(BuildContext context) {
    final laneWidth = roadWidth / laneCount;
    final carHeight = (roadHeight * 0.18).clamp(68.0, 108.0);
    final carWidth = carHeight * (186 / 373);

    return GestureDetector(
      onTapDown: (details) {
        final dx = details.localPosition.dx.clamp(0.0, roadWidth);
        final lane = (dx / laneWidth).floor().clamp(0, laneCount - 1);
        onTapLane(lane);
      },
      child: SizedBox(
        width: roadWidth,
        height: roadHeight,
        child: Stack(
          children: [
            Positioned.fill(
              child: CustomPaint(painter: _RoadPainter(laneCount: laneCount)),
            ),
            for (final item in items)
              Positioned(
                left: laneWidth * item.lane + laneWidth / 2 - item.size / 2,
                top: roadHeight * item.y,
                child: Image.asset(
                  item.asset,
                  width: item.size,
                  height: item.size,
                  fit: BoxFit.contain,
                ),
              ),
            AnimatedPositioned(
              duration: const Duration(milliseconds: 110),
              curve: Curves.easeOut,
              left: laneWidth * carLane + laneWidth / 2 - carWidth / 2,
              bottom: 6,
              child: Image.asset(
                'assets/rush_eggs/car.png',
                width: carWidth,
                height: carHeight,
                fit: BoxFit.contain,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _RoadPainter extends CustomPainter {
  const _RoadPainter({required this.laneCount});

  final int laneCount;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(
      Offset.zero & size,
      Paint()..color = const Color(0xFF979797),
    );

    final sidePaint = Paint()
      ..color = const Color(0xFFD8D8D8)
      ..strokeWidth = size.width * 0.011;

    final outerOffset = size.width * 0.025;
    final innerOffset = size.width * 0.065;

    canvas.drawLine(
      Offset(outerOffset, 0),
      Offset(outerOffset, size.height),
      sidePaint,
    );
    canvas.drawLine(
      Offset(innerOffset, 0),
      Offset(innerOffset, size.height),
      sidePaint,
    );
    canvas.drawLine(
      Offset(size.width - outerOffset, 0),
      Offset(size.width - outerOffset, size.height),
      sidePaint,
    );
    canvas.drawLine(
      Offset(size.width - innerOffset, 0),
      Offset(size.width - innerOffset, size.height),
      sidePaint,
    );

    final dashPaint = Paint()
      ..color = Colors.white
      ..strokeWidth = size.width * 0.008
      ..strokeCap = StrokeCap.square;

    final laneWidth = size.width / laneCount;
    final dashHeight = size.height * 0.042;
    final gapHeight = size.height * 0.034;

    for (int lane = 1; lane < laneCount; lane++) {
      final x = laneWidth * lane;
      double y = size.height * 0.015;
      while (y < size.height) {
        final y2 = math.min(y + dashHeight, size.height);
        canvas.drawLine(Offset(x, y), Offset(x, y2), dashPaint);
        y += dashHeight + gapHeight;
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _SharedRoadBackdrop extends StatelessWidget {
  const _SharedRoadBackdrop({required this.dimmed});

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

class _FullGameRoadBackdrop extends StatelessWidget {
  const _FullGameRoadBackdrop();

  @override
  Widget build(BuildContext context) {
    return const CustomPaint(
      painter: _RoadPainter(laneCount: 5),
      child: SizedBox.expand(),
    );
  }
}

class _FallingItem {
  const _FallingItem({
    required this.lane,
    required this.y,
    required this.speed,
    required this.size,
    required this.score,
    required this.asset,
  });

  final int lane;
  final double y;
  final double speed;
  final double size;
  final int score;
  final String asset;

  _FallingItem copyWith({
    int? lane,
    double? y,
    double? speed,
    double? size,
    int? score,
    String? asset,
  }) {
    return _FallingItem(
      lane: lane ?? this.lane,
      y: y ?? this.y,
      speed: speed ?? this.speed,
      size: size ?? this.size,
      score: score ?? this.score,
      asset: asset ?? this.asset,
    );
  }
}

class _GameItemTemplate {
  const _GameItemTemplate({
    required this.asset,
    required this.score,
    required this.size,
  });

  final String asset;
  final int score;
  final double size;
}

const List<_GameItemTemplate> _gameItemTemplates = <_GameItemTemplate>[
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_egg.png',
    score: 200,
    size: 40,
  ),
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_bottle.png',
    score: 300,
    size: 42,
  ),
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_ball.png',
    score: 250,
    size: 42,
  ),
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_luck.png',
    score: 200,
    size: 48,
  ),
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_tire.png',
    score: -410,
    size: 40,
  ),
  _GameItemTemplate(
    asset: 'assets/rush_eggs/item_pipe.png',
    score: -150,
    size: 46,
  ),
];
