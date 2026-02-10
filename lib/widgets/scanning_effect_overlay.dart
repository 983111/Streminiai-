import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/scanner_provider.dart';

class ScanningEffectOverlay extends ConsumerStatefulWidget {
  const ScanningEffectOverlay({super.key});

  @override
  ConsumerState<ScanningEffectOverlay> createState() => _ScanningEffectOverlayState();
}

class _ScanningEffectOverlayState extends ConsumerState<ScanningEffectOverlay> with TickerProviderStateMixin {
  late AnimationController _beamController;
  late AnimationController _pulseController;
  late Animation<double> _beamAnimation;
  
  // Internal state to mimic the web code's 'scanning' -> 'complete' flow
  // 'idle' | 'scanning' | 'complete'
  String _visualState = 'idle';
  Timer? _scanTimer;

  @override
  void initState() {
    super.initState();
    
    // Beam Animation (Top to Bottom) - Matches @keyframes scanWave
    _beamController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    );
    _beamAnimation = Tween<double>(begin: -0.2, end: 1.2).animate(
      CurvedAnimation(parent: _beamController, curve: Curves.linear),
    );

    // Grid Pulse Animation - Matches @keyframes pulseGrid
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    _beamController.dispose();
    _pulseController.dispose();
    _scanTimer?.cancel();
    super.dispose();
  }

  // Listen to the global scanner provider to trigger animations
  void _handleStateChange(bool isActive) {
    if (isActive && _visualState == 'idle') {
      // START SCANNING
      setState(() {
        _visualState = 'scanning';
      });
      _beamController.repeat();
      
      // Run scanning animation for 2.5s then switch to complete (Matches App.tsx logic)
      _scanTimer?.cancel();
      _scanTimer = Timer(const Duration(milliseconds: 2500), () {
        if (mounted && ref.read(scannerStateProvider).isActive) {
          setState(() {
            _visualState = 'complete';
          });
          _beamController.stop();
        }
      });
    } else if (!isActive && _visualState != 'idle') {
      // STOP / RESET
      _scanTimer?.cancel();
      _beamController.stop();
      setState(() {
        _visualState = 'idle';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // Watch the real scanner state from provider
    final scannerState = ref.watch(scannerStateProvider);
    
    // React to state changes
    ref.listen(scannerStateProvider, (previous, next) {
      _handleStateChange(next.isActive);
    });

    if (_visualState == 'idle') return const SizedBox.shrink();

    return Stack(
      children: [
        // 1. Darken Background (only during scanning)
        if (_visualState == 'scanning')
          Container(
            color: Colors.black.withOpacity(0.1),
          ),

        // 2. Tech Grid Background (Animated)
        if (_visualState == 'scanning')
          AnimatedBuilder(
            animation: _pulseController,
            builder: (context, child) {
              return Opacity(
                opacity: 0.1 + (_pulseController.value * 0.2), // 0.1 to 0.3 opacity
                child: CustomPaint(
                  painter: _GridPainter(),
                  size: Size.infinite,
                ),
              );
            },
          ),

        // 3. The Moving Scan Beam
        if (_visualState == 'scanning')
          AnimatedBuilder(
            animation: _beamController,
            builder: (context, child) {
              final height = MediaQuery.of(context).size.height;
              return Positioned(
                top: height * _beamAnimation.value,
                left: 0,
                right: 0,
                child: Container(
                  height: 250,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.blue.withOpacity(0.0),
                        Colors.blue.withOpacity(0.2),
                        Colors.blue.withOpacity(0.4),
                      ],
                    ),
                    border: const Border(
                      bottom: BorderSide(color: Colors.blueAccent, width: 4),
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.blue.withOpacity(0.6),
                        blurRadius: 20,
                        spreadRadius: 2,
                      )
                    ],
                  ),
                ),
              );
            },
          ),

        // 4. Threat Detected Banner (Complete State)
        // Matches the "Suspicious links found" banner
        AnimatedPositioned(
          duration: const Duration(milliseconds: 500),
          curve: Curves.easeOutBack,
          top: _visualState == 'complete' ? 60 : -150, // Slide in/out
          left: 16,
          right: 16,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFF7F1D1D).withOpacity(0.9), // red-900/95
                  border: Border.all(color: Colors.redAccent.withOpacity(0.5)),
                  borderRadius: BorderRadius.circular(16),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.3),
                      blurRadius: 15,
                      offset: const Offset(0, 8),
                    )
                  ],
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: Colors.red.withOpacity(0.2),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.gpp_maybe_rounded, color: Colors.redAccent, size: 24),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            "Threats Detected",
                            style: TextStyle(
                              color: Colors.white,
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                              letterSpacing: 0.5,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            "Suspicious links found in conversation.",
                            style: TextStyle(
                              color: Colors.red[100],
                              fontSize: 12,
                            ),
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
      ],
    );
  }
}

// Custom Painter for the Tech Grid
class _GridPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.blue
      ..strokeWidth = 1
      ..style = PaintingStyle.stroke;

    const double gridSize = 40;

    // Draw Vertical Lines
    for (double x = 0; x < size.width; x += gridSize) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), paint);
    }

    // Draw Horizontal Lines
    for (double y = 0; y < size.height; y += gridSize) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), paint);
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
