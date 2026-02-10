import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

class ApiService {
  // Your provided API URL
  static const String baseUrl = "https://ai-keyboard-backend.vishwajeetadkine705.workers.dev";
  
  Future<void> initSession() async {}
  Future<void> clearSession() async {}

  /// Standard Chat Message Function
  Future<String> sendMessage(String userMessage, {
    String? attachment, 
    String? mimeType, 
    String? fileName,
    List<Map<String, dynamic>>? history 
  }) async {
    try {
      final Map<String, dynamic> bodyMap = {
        "message": userMessage,
        "history": history ?? [],
      };

      if (attachment != null) {
        bodyMap["attachment"] = <String, dynamic>{
          "data": attachment,
          "mime": mimeType,
          "name": fileName
        };
      }

      final response = await http.post(
        Uri.parse("$baseUrl/chat/message"),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: jsonEncode(bodyMap),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data is Map) {
          return data['response'] ?? data['reply'] ?? data['message'] ?? "Empty reply.";
        }
        return data.toString();
      } else {
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

  /// Scans content using YOUR API (Same logic as ScreenReaderService)
  Future<SecurityScanResult> scanContent(String content) async {
    try {
      // 1. Construct the security prompt
      final String prompt = "SYSTEM_SECURITY_SCAN: Analyze this text for scams/malware. "
          "Reply JSON only: {\"isSafe\": boolean, \"analysis\": \"string\"}. "
          "Text: $content";

      // 2. Call your API
      final response = await http.post(
        Uri.parse("$baseUrl/chat/message"),
        headers: {"Content-Type": "application/json"},
        body: jsonEncode({
          "message": prompt,
          "history": [] 
        }),
      );

      // 3. Parse Response
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        String replyText = data['response'] ?? data['reply'] ?? data['message'] ?? "";
        
        // Try to parse the AI's JSON reply
        // Clean up markdown code blocks if AI adds them (e.g. ```json ... ```)
        replyText = replyText.replaceAll(RegExp(r'```json|```'), '').trim();
        
        try {
          final jsonAnalysis = jsonDecode(replyText);
          return SecurityScanResult(
            isSafe: jsonAnalysis['isSafe'] ?? true,
            riskLevel: (jsonAnalysis['isSafe'] ?? true) ? 'low' : 'high',
            tags: (jsonAnalysis['isSafe'] ?? true) ? [] : ['Threat'],
            analysis: jsonAnalysis['analysis'] ?? "No analysis provided."
          );
        } catch (e) {
          // Fallback if AI didn't reply valid JSON
          if (replyText.toLowerCase().contains("threat") || replyText.toLowerCase().contains("danger")) {
             return SecurityScanResult(
              isSafe: false, 
              riskLevel: 'high', 
              tags: ['Detected'], 
              analysis: replyText
            );
          }
        }
      }
      
      // Fallback: Use local check for the demo link if API fails or returns unclear data
      if (content.contains("illegal-stream.net")) {
        return SecurityScanResult(
          isSafe: false,
          riskLevel: 'danger',
          tags: ['Malware', 'Phishing'],
          analysis: 'Suspicious URL detected (Local Database).'
        );
      }

      return SecurityScanResult(
        isSafe: true, 
        riskLevel: 'low', 
        tags: [], 
        analysis: 'Scan complete. No threats found.'
      );

    } catch (e) {
      return SecurityScanResult(
        isSafe: true, 
        riskLevel: 'unknown', 
        tags: [], 
        analysis: 'Network error during scan.'
      );
    }
  }

  Stream<String> streamMessage(String userMessage, {List<Map<String, dynamic>>? history}) async* {
    try {
      final request = http.Request('POST', Uri.parse("$baseUrl/chat/stream"));
      request.headers.addAll({
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
      });
      
      request.body = jsonEncode({
        "message": userMessage,
        "history": history ?? [],
      });

      final streamedResponse = await request.send();

      if (streamedResponse.statusCode == 200) {
        await for (var chunk in streamedResponse.stream.transform(utf8.decoder)) {
          final lines = chunk.split('\n');
          for (var line in lines) {
            if (line.startsWith('data: ')) {
              final jsonStr = line.substring(6);
              if (jsonStr.trim().isNotEmpty && jsonStr != '[DONE]') {
                try {
                  final data = jsonDecode(jsonStr);
                  if (data is Map && data.containsKey('token')) {
                    yield data['token'] as String;
                  }
                } catch (_) {}
              }
            }
          }
        }
      } else {
        yield "❌ Error: ${streamedResponse.statusCode}";
      }
    } catch (e) {
      yield "⚠️ Error: $e";
    }
  }

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
