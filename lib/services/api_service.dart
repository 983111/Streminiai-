import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import '../core/config/env_config.dart';

class ApiService {
  static const String baseUrl = EnvConfig.baseUrl;
  static const String githubAgentUrl = EnvConfig.githubAgentUrl;

  Future<void> initSession() async {}
  Future<void> clearSession() async {}

  // ── Standard chat ─────────────────────────────────────────────────────────
  Future<String> sendMessage(
    String userMessage, {
    String? attachment,
    String? mimeType,
    String? fileName,
    List<Map<String, dynamic>>? history,
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
          "name": fileName,
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
          final e = jsonDecode(response.body);
          return "❌ Error: ${e['error'] ?? response.statusCode}";
        } catch (_) {
          return "❌ Error ${response.statusCode}: ${response.body}";
        }
      }
    } catch (e) {
      return "⚠️ Network Error: $e";
    }
  }

  // ── Document chat ─────────────────────────────────────────────────────────
  Future<String> sendDocumentMessage({
    required String documentText,
    required String question,
    List<Map<String, dynamic>>? history,
  }) async {
    try {
      final response = await http.post(
        Uri.parse("$baseUrl/chat/document"),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: jsonEncode({
          "documentText": documentText,
          "question": question,
          "history": history ?? [],
        }),
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['response'] ?? "Empty reply.";
      } else {
        try {
          final e = jsonDecode(response.body);
          return "❌ Error: ${e['error'] ?? response.statusCode}";
        } catch (_) {
          return "❌ Error ${response.statusCode}: ${response.body}";
        }
      }
    } catch (e) {
      return "⚠️ Network Error: $e";
    }
  }

  // ── Streaming ─────────────────────────────────────────────────────────────
  Stream<String> streamMessage(
    String userMessage, {
    List<Map<String, dynamic>>? history,
  }) async* {
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
        await for (final chunk in streamedResponse.stream.transform(utf8.decoder)) {
          for (final line in chunk.split('\n')) {
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

  // ── GitHub Agent ──────────────────────────────────────────────────────────
  // FIX 1: maxClientIterations raised from 5 → 20.
  //        Complex repos need 8-15 file reads before the AI can produce a fix.
  //        The old cap of 5 made the client bail out with a fake "infinite loop"
  //        error before the Worker ever finished its job.
  //
  // FIX 2: History construction no longer triggers the Worker's duplicate-
  //        response guard. The Worker (index.js lines 74-85) checks if the last
  //        two assistant messages in history are identical. The old code always
  //        wrote '<read_file path="$nextFile" />' as the assistant message,
  //        which after trimHistory() could leave two identical assistant entries
  //        and cause the Worker to abort with "AI is producing identical
  //        responses". The fix: keep history exactly as the HTML frontend does —
  //        assistant = the AI's actual <read_file/> tag (unique per file),
  //        user = the file content injected as context.
  Future<GithubAgentRunResult> processGithubAgentTask({
    required String repoOwner,
    required String repoName,
    required String task,
  }) async {
    final startedAt = DateTime.now();
    final visitedFiles = <String>[];
    final history = <Map<String, dynamic>>[];
    const maxClientIterations = 20; // was 5 — raised to match Worker tolerance
    var iteration = 0;

    try {
      while (true) {
        // Client-side safety cap (Worker has its own 50-iteration guard)
        if (iteration >= maxClientIterations) {
          return GithubAgentRunResult(
            status: 'ERROR',
            summary:
                'Stopped after $maxClientIterations iterations. '
                'The agent could not complete the task within the allowed steps.',
            rawPayload: '',
            visitedFiles: visitedFiles,
            iterationCount: iteration,
            duration: DateTime.now().difference(startedAt),
          );
        }

        final response = await http.post(
          Uri.parse(githubAgentUrl),
          headers: {
            "Content-Type": "application/json",
            "Accept": "application/json",
          },
          body: jsonEncode({
            "agentName": "stremini architect",
            "repoOwner": repoOwner,
            "repoName": repoName,
            "task":
                '$task\n\nConstraints:\n'
                '- Do not push or deploy changes.\n'
                '- Return corrected code only (no AI reasoning).',
            "history": history,
            "readFiles": visitedFiles,
            "iteration": iteration,
            "allowPush": false,
            "outputFormat": "code_only",
          }),
        );

        if (response.statusCode != 200) {
          return GithubAgentRunResult(
            status: 'ERROR',
            summary: 'Server Error: ${response.statusCode}',
            rawPayload: response.body,
            visitedFiles: visitedFiles,
            iterationCount: iteration,
            duration: DateTime.now().difference(startedAt),
          );
        }

        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final status = data['status']?.toString() ?? 'ERROR';

        if (status == 'CONTINUE') {
          final nextFile = data['nextFile']?.toString() ?? 'unknown file';
          final fileContent = data['fileContent']?.toString() ?? '';

          // Build history correctly — assistant message is the unique
          // <read_file/> tag the AI produced (different for every file, so the
          // Worker's duplicate-response guard never fires). User message is the
          // file content so the AI has it as context on the next iteration.
          history.addAll([
            {
              'role': 'assistant',
              'content': '<read_file path="$nextFile" />',
            },
            {
              'role': 'user',
              'content': 'File content of $nextFile:\n\n$fileContent',
            },
          ]);

          // Sync visitedFiles from Worker response (Worker is authoritative)
          if (data['readFiles'] is List) {
            visitedFiles
              ..clear()
              ..addAll(List<String>.from(data['readFiles'] as List));
          } else if (!visitedFiles.contains(nextFile)) {
            visitedFiles.add(nextFile);
          }

          // Use Worker's iteration counter as source of truth
          iteration = data['iteration'] is int
              ? data['iteration'] as int
              : iteration + 1;

          continue;
        }

        // Terminal states: COMPLETED, FIXED, ERROR
        return GithubAgentRunResult(
          status: status,
          summary: _summaryFromResponse(data),
          rawPayload: const JsonEncoder.withIndent('  ').convert(data),
          visitedFiles: visitedFiles,
          iterationCount: iteration,
          duration: DateTime.now().difference(startedAt),
          filePath: data['filePath']?.toString(),
          pushed: data['pushed'] == true,
        );
      }
    } catch (e) {
      return GithubAgentRunResult(
        status: 'ERROR',
        summary: 'Network Error: $e',
        rawPayload: '',
        visitedFiles: visitedFiles,
        iterationCount: iteration,
        duration: DateTime.now().difference(startedAt),
      );
    }
  }

  String _summaryFromResponse(Map<String, dynamic> data) {
    final status = data['status'];
    switch (status) {
      case 'COMPLETED':
        final solution = _extractCodeOnly(data['solution']?.toString() ?? '');
        return solution.isNotEmpty
            ? solution
            : 'No corrected code returned for status $status.';
      case 'FIXED':
        final filePath = data['filePath']?.toString();
        final fixedContent =
            _extractCodeOnly(data['fixedContent']?.toString() ?? '');
        if (fixedContent.isNotEmpty) {
          if (filePath == null || filePath.isEmpty) return fixedContent;
          return '// File: $filePath\n$fixedContent';
        }
        final fallback = _extractCodeOnly(
          data['solution']?.toString() ??
              data['patch']?.toString() ??
              data['fix']?.toString() ??
              data['pushMessage']?.toString() ??
              '',
        );
        return fallback.isNotEmpty
            ? fallback
            : 'Corrected code generated. No file content was returned.';
      case 'ERROR':
        return data['message']?.toString() ?? 'Agent returned an error.';
      default:
        return 'Unexpected response status: ${data['status']}';
    }
  }

  String _extractCodeOnly(String input) {
    final fenceRegex = RegExp(r'```(?:[a-zA-Z0-9_+-]+)?\n([\s\S]*?)```');
    final matches = fenceRegex.allMatches(input).toList();
    if (matches.isEmpty) return input.trim();
    return matches
        .map((m) => (m.group(1) ?? '').trim())
        .where((part) => part.isNotEmpty)
        .join('\n\n');
  }

  // ── Security scan ─────────────────────────────────────────────────────────
  Future<SecurityScanResult> scanContent(String content) async {
    try {
      final response = await http.post(
        Uri.parse("$baseUrl/scan-content"),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: jsonEncode({"content": content}),
      );
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        List<String> extractedTags = [];
        if (data['taggedElements'] != null) {
          extractedTags = (data['taggedElements'] as List)
              .map<String>((e) => e['matchedText']?.toString() ?? '')
              .where((s) => s.isNotEmpty)
              .toList();
        }
        return SecurityScanResult(
          isSafe: data['isSafe'] ?? false,
          riskLevel: data['riskLevel'] ?? 'unknown',
          tags: extractedTags,
          analysis: data['summary'] ?? 'No analysis provided',
        );
      } else {
        String errorMsg = "Scan failed (${response.statusCode})";
        try {
          final e = jsonDecode(response.body);
          if (e['error'] != null) errorMsg = e['error'];
        } catch (_) {}
        return SecurityScanResult(
          isSafe: false,
          riskLevel: 'error',
          tags: [],
          analysis: errorMsg,
        );
      }
    } catch (e) {
      return SecurityScanResult(
        isSafe: false,
        riskLevel: 'error',
        tags: [],
        analysis: "Network Error: $e",
      );
    }
  }

  // Stubs
  Future<VoiceCommandResult> parseVoiceCommand(String command) async =>
      VoiceCommandResult(action: '', parameters: {});
  Future<String> translateScreen(String content, String targetLanguage) async =>
      "";
  Future<String> completeText(String incompleteText) async => "";
  Future<String> rewriteInTone(String text, String tone) async => "";
  Future<String> translateText(String text, String targetLanguage) async => "";
  Future<Map<String, dynamic>> checkHealth() async => {};
}

// ── Models ────────────────────────────────────────────────────────────────────

class SecurityScanResult {
  final bool isSafe;
  final String riskLevel;
  final List<String> tags;
  final String analysis;

  SecurityScanResult({
    required this.isSafe,
    required this.riskLevel,
    required this.tags,
    required this.analysis,
  });
}

class VoiceCommandResult {
  final String action;
  final Map<String, dynamic> parameters;

  VoiceCommandResult({required this.action, required this.parameters});
}

class GithubAgentRunResult {
  final String status;
  final String summary;
  final String rawPayload;
  final List<String> visitedFiles;
  final int iterationCount;
  final Duration duration;
  final String? filePath;
  final bool pushed;

  const GithubAgentRunResult({
    required this.status,
    required this.summary,
    required this.rawPayload,
    required this.visitedFiles,
    required this.iterationCount,
    required this.duration,
    this.filePath,
    this.pushed = false,
  });
}

final apiServiceProvider = Provider<ApiService>((ref) => ApiService());
