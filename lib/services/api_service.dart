import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

class ApiService {
  static const String baseUrl = "https://ai-keyboard-backend.vishwajeetadkine705.workers.dev";
  
  Future<void> initSession() async {}
  Future<void> clearSession() async {}

  /// Sends a message to the Gemini Backend with History
  Future<String> sendMessage(String userMessage, {
    String? attachment, 
    String? mimeType, 
    String? fileName,
    List<Map<String, dynamic>>? history 
  }) async {
    try {
      // 1. Prepare Body with History
      final Map<String, dynamic> bodyMap = {
        "message": userMessage,
        "history": history ?? [], 
      };

      // 2. Add Attachment if exists
      if (attachment != null) {
        bodyMap["attachment"] = <String, dynamic>{
          "data": attachment,
          "mime": mimeType,
          "name": fileName
        };
      }

      // 3. Make the API Call
      final response = await http.post(
        Uri.parse("$baseUrl/chat/message"),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: jsonEncode(bodyMap),
      );

      // 4. Handle Response
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data is Map) {
          // Check for various response keys used by different backend versions
          return data['response'] ?? data['reply'] ?? data['message'] ?? "Empty reply.";
        }
        return data.toString();
      } else {
        // Parse error message for debugging
        try {
          final errData = jsonDecode(response.body);
          return "❌ Error: ${errData['error'] ?? response.statusCode}";
        } catch (_) {
          return "❌ Error ${response.statusCode}: ${response.body}";
        }
      }
    } catch (e) {
      return "⚠️ Network Error: $e";
    }
  }

  /// Replicates the "Scam Detection" logic from the web code (BackgroundSim.tsx)
  Future<SecurityScanResult> scanContent(String content) async {
    // Simulate network delay for realism
    await Future.delayed(const Duration(milliseconds: 1500));

    // Logic replicated from web code: Check for suspicious links
    if (content.contains("illegal-stream.net") || content.contains("suspicious")) {
      return SecurityScanResult(
        isSafe: false, 
        riskLevel: 'danger', 
        tags: ['Phishing', 'Malware'], 
        analysis: 'Suspicious URL detected: illegal-stream.net'
      );
    } 
    
    if (content.contains("wikipedia.org")) {
       return SecurityScanResult(
        isSafe: true, 
        riskLevel: 'safe', 
        tags: ['Verified', 'Safe'], 
        analysis: 'Official verified source.'
      );
    }

    // Default safe state
    return SecurityScanResult(
      isSafe: true, 
      riskLevel: 'low', 
      tags: [], 
      analysis: 'No threats detected.'
    ); 
  }

  // Placeholder methods for other features
  Stream<String> streamMessage(String userMessage, {List<Map<String, dynamic>>? history}) async* { yield "Stream not implemented"; }
  Future<VoiceCommandResult> parseVoiceCommand(String command) async { return VoiceCommandResult(action: '', parameters: {}); }
  Future<String> translateScreen(String content, String targetLanguage) async { return ""; }
  Future<String> completeText(String incompleteText) async { return ""; }
  Future<String> rewriteInTone(String text, String tone) async { return ""; }
  Future<String> translateText(String text, String targetLanguage) async { return ""; }
  Future<Map<String, dynamic>> checkHealth() async { return {}; }
}

class SecurityScanResult {
  final bool isSafe; 
  final String riskLevel; 
  final List<String> tags; 
  final String analysis;
  
  SecurityScanResult({
    required this.isSafe, 
    required this.riskLevel, 
    required this.tags, 
    required this.analysis
  });
}

class VoiceCommandResult {
  final String action; 
  final Map<String, dynamic> parameters;
  VoiceCommandResult({required this.action, required this.parameters});
}

final apiServiceProvider = Provider<ApiService>((ref) => ApiService());
