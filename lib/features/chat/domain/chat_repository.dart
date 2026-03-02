abstract class ChatRepository {
  Future<String> sendMessage({
    required String message,
    List<Map<String, dynamic>> history,
    String? attachment,
    String? mimeType,
    String? fileName,
  });

  Future<String> sendDocumentMessage({
    required String documentText,
    required String question,
    List<Map<String, dynamic>> history,
  });
}
