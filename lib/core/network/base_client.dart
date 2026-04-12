import 'dart:convert';
import 'package:http/http.dart' as http;

// Strips any URL / hostname from error messages before they surface to the user.
String _sanitizeNetworkError(Object e) {
  String raw = e.toString();
  raw = raw.replaceAll(RegExp(r'https?://[^\s,]+'), 'the server');
  raw = raw.replaceAll(RegExp(r'[a-zA-Z0-9._-]+\.workers\.dev[^\s,]*'), 'the server');
  raw = raw.replaceAll(RegExp(r'[a-zA-Z0-9._-]+\.[a-zA-Z]{2,6}(:\d+)?(/[^\s]*)?'), 'the server');
  raw = raw
      .replaceAll('SocketException:', 'Network error:')
      .replaceAll('ClientException:', '')
      .replaceAll('HandshakeException:', 'Secure connection error:')
      .replaceAll('Exception:', '')
      .trim();

  if (raw.toLowerCase().contains('failed host lookup') ||
      raw.toLowerCase().contains('network is unreachable') ||
      raw.toLowerCase().contains('no address associated') ||
      raw.toLowerCase().contains('nodename nor servname')) {
    return 'No internet connection. Please check your network and try again.';
  }
  if (raw.toLowerCase().contains('timed out') || raw.toLowerCase().contains('timeout')) {
    return 'Connection timed out. Please try again.';
  }
  if (raw.isEmpty) return 'Something went wrong. Please try again.';
  return raw;
}

class BaseClient {
  const BaseClient(this._httpClient);

  final http.Client _httpClient;

  Future<Map<String, dynamic>> postJson(
    Uri uri,
    Map<String, dynamic> body,
  ) async {
    try {
      final response = await _httpClient.post(
        uri,
        headers: const {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
        body: jsonEncode(body),
      );

      final dynamic decoded = response.body.isEmpty ? {} : jsonDecode(response.body);
      if (response.statusCode < 200 || response.statusCode >= 300) {
        // Never include URL or status code that hints at backend in error
        throw Exception('Unable to get a response. Please try again.');
      }

      return decoded is Map<String, dynamic>
          ? decoded
          : <String, dynamic>{'data': decoded.toString()};
    } catch (e) {
      // Re-throw with sanitized message
      throw Exception(_sanitizeNetworkError(e));
    }
  }
}