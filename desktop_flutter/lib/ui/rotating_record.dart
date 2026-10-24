import 'package:flutter/material.dart';

class RotatingRecord extends StatefulWidget {
  const RotatingRecord({super.key, required this.playing, required this.child});
  final bool playing;
  final Widget child;

  @override
  State<RotatingRecord> createState() => _RotatingRecordState();
}

class _RotatingRecordState extends State<RotatingRecord>
    with SingleTickerProviderStateMixin {
  late final AnimationController rotation;

  @override
  void initState() {
    super.initState();
    rotation = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 20),
    );
  }

  void updateAnimation() {
    final motion = !MediaQuery.disableAnimationsOf(context);
    if (widget.playing && motion) {
      if (!rotation.isAnimating) rotation.repeat();
    } else {
      rotation.stop();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    updateAnimation();
  }

  @override
  void didUpdateWidget(RotatingRecord oldWidget) {
    super.didUpdateWidget(oldWidget);
    updateAnimation();
  }

  @override
  void dispose() {
    rotation.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: RotationTransition(
      turns: rotation,
      child: ClipOval(child: widget.child),
    ),
  );
}
