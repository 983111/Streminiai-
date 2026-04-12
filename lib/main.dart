import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'package:stremini_chatbot/providers/scanner_provider.dart';
import 'core/localization/app_strings.dart';
import 'core/native/android_native_bridge_service.dart';
import 'core/native/native_bridge_service.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/presentation/auth_gate.dart';
import 'providers/app_settings_provider.dart';
import 'utils/session_lifecycle_manager.dart';

const _supabaseUrl = String.fromEnvironment(
  'SUPABASE_URL',
  defaultValue: 'https://libbzwesgiqwkackexzl.supabase.co',
);
const _supabaseAnonKey = String.fromEnvironment(
  'SUPABASE_ANON_KEY',
  defaultValue:
      'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxpYmJ6d2VzZ2lxd2thY2tleHpsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU5MDEwNzgsImV4cCI6MjA5MTQ3NzA3OH0.h0War5wAbQil1hP-igImCABgUeBtuWYNLcEhrHw5qxI',
);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Supabase.initialize(
    url: _supabaseUrl,
    anonKey: _supabaseAnonKey,
    authOptions: const FlutterAuthClientOptions(
      authFlowType: AuthFlowType.pkce,
    ),
  );

  runApp(const ProviderScope(child: MyApp()));
}

class MyApp extends ConsumerStatefulWidget {
  const MyApp({super.key});

  @override
  ConsumerState<MyApp> createState() => _MyAppState();
}

class _MyAppState extends ConsumerState<MyApp> {
  static ProviderContainer? globalContainer;
  final NativeBridgeService _nativeBridge = AndroidNativeBridgeService();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_MyAppState.globalContainer == null) {
      _MyAppState.globalContainer = ProviderScope.containerOf(context);
      _setupScannerListeners();
    }
  }

  void _setupScannerListeners() {
    if (_MyAppState.globalContainer == null) return;

    _nativeBridge.initialize(onEvent: (method) async {
      final notifier =
          _MyAppState.globalContainer!.read(scannerStateProvider.notifier);
      switch (method) {
        case 'startScanner':
          await notifier.startScanning();
          break;
        case 'stopScanner':
          await notifier.stopScanning();
          break;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    // CRITICAL FIX: Watch appSettingsProvider here so that any change to theme
    // or language immediately triggers a rebuild of MaterialApp, applying the
    // new ThemeMode and Locale to the ENTIRE widget tree.
    final settings = ref.watch(appSettingsProvider);

    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: AppStrings.t(settings.language, 'app_title'),

      // These two lines are what make theme/language work app-wide.
      // settings.themeMode and settings.locale update whenever the user
      // changes them in Settings → the whole app reflects the change instantly.
      themeMode: settings.themeMode,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,

      locale: settings.locale,
      supportedLocales: const [
        Locale('en'),
        Locale('hi'),
        Locale('es'),
        Locale('fr'),
        Locale('ar'),
        Locale('ja'),
      ],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],

      home: const SessionLifecycleManager(
        child: AuthGate(),
      ),
    );
  }
}