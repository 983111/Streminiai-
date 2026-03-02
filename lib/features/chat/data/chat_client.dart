import '../../../core/config/app_config.dart';
import '../../../core/network/base_client.dart';

class ChatClient {
  const ChatClient(this._baseClient);

  final BaseClient _baseClient;

  Future<String> sendMessage({
    required String message,
    List<Map<String, dynamic>> history = const [],
    String? attachment,
    String? mimeType,
    String? fileName,
  }) async {
    final body = <String, dynamic>{
      'message': message,
      'history': history,
    };

    if (attachment != null) {
      body['attachment'] = {
        'data': attachment,
        'mime': mimeType,
        'name': fileName,
      };
    }

    final data = await _baseClient.postJson(
      Uri.parse('${AppConfig.baseUrl}/chat/message'),
      body,
    );

    return (data['response'] ?? data['reply'] ?? data['message'] ?? 'Empty reply.').toString();
  }

  Future<String> sendDocumentMessage({
    required String documentText,
    required String question,
    List<Map<String, dynamic>> history = const [],
  }) async {
    final data = await _baseClient.postJson(
      Uri.parse('${AppConfig.baseUrl}/chat/document'),
      {
        'documentText': documentText,
        'question': question,
        'history': history,
      },
    );
    return (data['response'] ?? 'Empty reply.').toString();
  }
}
