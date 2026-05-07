import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'app_backdrop.dart';

class AppRoadLoader extends StatelessWidget {
  const AppRoadLoader({super.key, this.showBackdrop = false});

  final bool showBackdrop;

  @override
  Widget build(BuildContext context) {
    final loader = Stack(
      children: [
        Positioned.fill(
          child: ColoredBox(color: Colors.black.withValues(alpha: 0.48)),
        ),
        SafeArea(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final double indicatorSize = math.min(
                constraints.maxWidth * 0.26,
                90,
              );
              final double carWidth = math.min(constraints.maxWidth * 0.3, 100);

              return Stack(
                children: [
                  Align(
                    alignment: const Alignment(0, -0.08),
                    child: SizedBox(
                      width: indicatorSize,
                      height: indicatorSize,
                      child: const _DotRingLoader(),
                    ),
                  ),
                  Align(
                    alignment: const Alignment(0, 0.62),
                    child: Image.asset(
                      'assets/rush_eggs/car.png',
                      width: carWidth,
                      fit: BoxFit.contain,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ],
    );

    if (!showBackdrop) {
      return loader;
    }

    return Stack(children: [const AppBackdrop(), loader]);
  }
}

class _DotRingLoader extends StatefulWidget {
  const _DotRingLoader();

  @override
  State<_DotRingLoader> createState() => _DotRingLoaderState();
}

class _DotRingLoaderState extends State<_DotRingLoader>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  )..repeat();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return CustomPaint(
          painter: _DotRingPainter(progress: _controller.value),
        );
      },
    );
  }
}

class _DotRingPainter extends CustomPainter {
  const _DotRingPainter({required this.progress});

  final double progress;

  static const int _dotCount = 14;

  @override
  void paint(Canvas canvas, Size size) {
    final Offset center = size.center(Offset.zero);
    final double radius = size.shortestSide * 0.34;
    final double baseDotSize = size.shortestSide * 0.038;
    final double activeDotSize = size.shortestSide * 0.075;
    final double head = progress * _dotCount;
    final Paint paint = Paint()..style = PaintingStyle.fill;

    for (int index = 0; index < _dotCount; index++) {
      final double angle = (-math.pi / 2) + ((2 * math.pi * index) / _dotCount);
      double distance = (head - index).remainder(_dotCount.toDouble());
      if (distance < 0) {
        distance += _dotCount;
      }

      double emphasis = 0;
      if (distance < 4) {
        emphasis = 1 - (distance / 4);
      }

      final double dotSize =
          baseDotSize + ((activeDotSize - baseDotSize) * emphasis);
      final double alpha = 0.16 + (0.84 * emphasis);
      final Offset offset = Offset(
        center.dx + (math.cos(angle) * radius),
        center.dy + (math.sin(angle) * radius),
      );

      paint.color = Colors.white.withValues(alpha: alpha);
      canvas.drawCircle(offset, dotSize, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _DotRingPainter oldDelegate) {
    return oldDelegate.progress != progress;
  }
}
