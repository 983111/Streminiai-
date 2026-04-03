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
          return data['response'] ??
              data['reply'] ??
              data['message'] ??
              "Empty reply.";
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
      final request =
          http.Request('POST', Uri.parse("$baseUrl/chat/stream"));
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
        await for (final chunk
            in streamedResponse.stream.transform(utf8.decoder)) {
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

  // ── GitHub Agent — single step (called per iteration by the screen) ───────
  //
  // The agent screen drives the loop itself (stremini_agent_screen.dart).
  // Each call sends the current history + visitedFiles + iteration counter
  // and returns the raw decoded response map from the Worker.
  //
  // Worker status values:
  //   "CONTINUE"   → action "read_file" or "more_files" or "already_read"
  //   "FIXED"      → terminal — fixedContent / filePath present
  //   "COMPLETED"  → terminal — solution present
  //   "ERROR"      → terminal — message present
  //
  Future<Map<String, dynamic>> githubAgentStep({
    required String repoOwner,
    required String repoName,
    required String task,
    required List<Map<String, dynamic>> history,
    required List<String> visitedFiles,
    required int iteration,
    List<Map<String, dynamic>> outputFiles = const [],
    String agentMode = "fix",
    bool allowPush = false,
  }) async {
    try {
      final response = await http.post(
        Uri.parse(githubAgentUrl),
        headers: {
          "Content-Type": "application/json",
          "Accept": "application/json",
        },
        body: jsonEncode({
          "repoOwner": repoOwner,
          "repoName": repoName,
          "task": task,
          "history": history,
          "readFiles": visitedFiles,
          "outputFiles": outputFiles,   // ← pass accumulated output files back
          "iteration": iteration,
          "agentMode": agentMode,
          "allowPush": allowPush,
        }),
      );

      if (response.statusCode != 200) {
        return {
          "status": "ERROR",
          "message":
              "Server error ${response.statusCode}: ${response.body.substring(0, response.body.length.clamp(0, 300))}",
        };
      }

      final data = jsonDecode(response.body);
      if (data is Map<String, dynamic>) return data;

      return {
        "status": "ERROR",
        "message": "Unexpected response shape from worker.",
      };
    } catch (e) {
      return {
        "status": "ERROR",
        "message": "Network error: $e",
      };
    }
  }

  // ── GitHub Agent — all-in-one loop (legacy / background use) ─────────────
  //
  // This method runs the full multi-step loop internally and returns a single
  // GithubAgentRunResult. Use githubAgentStep for interactive UIs that want
  // live progress logging (like StreminiAgentScreen).
  //
  Future<GithubAgentRunResult> processGithubAgentTask({
    required String repoOwner,
    required String repoName,
    required String task,
    String agentMode = "fix",
  }) async {
    final startedAt = DateTime.now();
    final visitedFiles = <String>[];
    final outputFiles = <Map<String, dynamic>>[];
    final history = <Map<String, dynamic>>[];
    const maxClientIterations = 20;
    var iteration = 0;

    try {
      while (true) {
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

        final response = await githubAgentStep(
          repoOwner: repoOwner,
          repoName: repoName,
          task: task,
          history: history,
          visitedFiles: visitedFiles,
          iteration: iteration,
          outputFiles: outputFiles,
          agentMode: agentMode,
        );

        final status = response['status']?.toString() ?? 'ERROR';

        if (status == 'CONTINUE') {
          final nextFile = response['nextFile']?.toString() ?? '';
          final fileContent = response['fileContent']?.toString() ?? '';
          final action = response['action']?.toString() ?? 'read_file';

          // Sync Worker's authoritative readFiles list
          if (response['readFiles'] is List) {
            visitedFiles
              ..clear()
              ..addAll(List<String>.from(response['readFiles'] as List));
          } else if (nextFile.isNotEmpty &&
              !visitedFiles.contains(nextFile)) {
            visitedFiles.add(nextFile);
          }

          // Sync accumulated output files from Worker
          if (response['outputFiles'] is List) {
            final workerOutputFiles =
                List<Map<String, dynamic>>.from(
                    (response['outputFiles'] as List)
                        .map((e) => Map<String, dynamic>.from(e as Map)));
            outputFiles
              ..clear()
              ..addAll(workerOutputFiles);
          }

          // Update iteration from Worker's counter
          iteration = response['iteration'] is int
              ? response['iteration'] as int
              : iteration + 1;

          // Only add history for actual file reads (not already_read notices)
          if (action == 'read_file' && nextFile.isNotEmpty) {
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
          }

          continue;
        }

        // Terminal state
        return GithubAgentRunResult(
          status: status,
          summary: _summaryFromResponse(response),
          rawPayload: const JsonEncoder.withIndent('  ').convert(response),
          visitedFiles: visitedFiles,
          iterationCount: iteration,
          duration: DateTime.now().difference(startedAt),
          filePath: response['filePath']?.toString(),
          pushed: response['pushed'] == true,
          outputFiles: outputFiles,
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
        final solution =
            _extractCodeOnly(data['solution']?.toString() ?? '');
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
              .map<String>(
                  (e) => e['matchedText']?.toString() ?? '')
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
  Future<String> translateScreen(
          String content, String targetLanguage) async =>
      "";
  Future<String> completeText(String incompleteText) async => "";
  Future<String> rewriteInTone(String text, String tone) async => "";
  Future<String> translateText(
          String text, String targetLanguage) async =>
      "";
  Future<Map<String, dynamic>> checkHealth() async => {};
}

// ── Models ─────────────────────────────────────────────────────────────────────

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
  final List<Map<String, dynamic>> outputFiles;

  const GithubAgentRunResult({
    required this.status,
    required this.summary,
    required this.rawPayload,
    required this.visitedFiles,
    required this.iterationCount,
    required this.duration,
    this.filePath,
    this.pushed = false,
    this.outputFiles = const [],
  });
}

final apiServiceProvider = Provider<ApiService>((ref) => ApiService());
