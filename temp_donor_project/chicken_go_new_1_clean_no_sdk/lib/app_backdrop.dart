import 'package:flutter/material.dart';

class AppBackdrop extends StatelessWidget {
  const AppBackdrop({super.key, this.dimmed = true});

  final bool dimmed;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final double borderWidth = constraints.maxWidth * 0.03;
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
