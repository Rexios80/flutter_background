/// Represents the importance of an android notification as described
/// under https://developer.android.com/training/notify-user/channels#importance.
enum AndroidNotificationImportance {
  // Low and min importance levels apparantly are not supported, see
  // https://github.com/JulianAssmann/flutter_background/issues/37 for more.

  // Min,
  // Low,
  Default(0),
  High(1),
  Max(2);

  final int value;

  const AndroidNotificationImportance(this.value);
}

// Represents the information required to get an Android resource.
// See https://developer.android.com/reference/android/content/res/Resources for reference.
class AndroidResource {
  // The name of the desired resource.
  final String name;

  // Optional default resource type to find, if "type/" is not included in the name. Can be null to require an explicit type.
  final String defType;

  const AndroidResource({required this.name, this.defType = 'drawable'});
}

enum AndroidForegroundServiceType {
  location(8),
  camera(64),
  microphone(128);

  final int value;

  const AndroidForegroundServiceType(this.value);

  static int combine(List<AndroidForegroundServiceType> types) =>
      types.fold(0, (p, e) => p | e.value);
}

/// Android configuration for the [FlutterBackground] plugin.
class FlutterBackgroundAndroidConfig {
  /// The importance of the notification used for the foreground service.
  final AndroidNotificationImportance notificationImportance;

  /// The title used for the foreground service notification.
  final String notificationTitle;

  /// The body used for the foreground service notification.
  final String notificationText;

  /// The resource name of the icon to be used for the foreground notification.
  final AndroidResource notificationIcon;

  /// When enabled, a WifiLock is acquired when background execution is started.
  /// This allows the application to keep the Wi-Fi radio awake, even when the
  /// user has not used the device in a while (e.g. for background network
  /// communications).
  final bool enableWifiLock;

  /// When enabled, request permission to disable battery optimizations.
  /// This is enabled by default, and should only be disabled on platforms that
  /// do not support it (ex: Wear OS).
  final bool shouldRequestBatteryOptimizationsOff;

  /// The foreground services types to use to start the service.
  ///
  /// These are required if you are using any of these features in the
  /// background: location, camera, microphone.
  final List<AndroidForegroundServiceType> foregroundServiceTypes;

  /// Creates an Android specific configuration for the [FlutterBackground] plugin.
  ///
  /// [notificationTitle] is the title used for the foreground service notification.
  /// [notificationText] is the body used for the foreground service notification.
  /// [notificationImportance] is the importance of the foreground service notification.
  /// [notificationIcon] must be a drawable resource.
  /// E. g. if the icon with name "background_icon" is in the "drawable" resource folder,
  /// it should be of value `AndroidResource(name: 'background_icon', defType: 'drawable').
  /// [enableWifiLock] indicates wether or not a WifiLock is acquired, when the
  /// background execution is started. This allows the application to keep the
  /// Wi-Fi radio awake, even when the user has not used the device in a while.
  const FlutterBackgroundAndroidConfig({
    this.notificationTitle = 'Notification title',
    this.notificationText = 'Notification text',
    this.notificationImportance = AndroidNotificationImportance.Default,
    this.notificationIcon =
        const AndroidResource(name: 'ic_launcher', defType: 'mipmap'),
    this.enableWifiLock = true,
    this.shouldRequestBatteryOptimizationsOff = true,
    this.foregroundServiceTypes = const [],
  });
}
