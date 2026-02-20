import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme/app_colors.dart';
import '../core/theme/app_text_styles.dart';
import '../core/widgets/app_container.dart';
import '../services/api_service.dart';

class StreminiAgentScreen extends ConsumerStatefulWidget {
  const StreminiAgentScreen({super.key});

  @override
  ConsumerState<StreminiAgentScreen> createState() => _StreminiAgentScreenState();
}

class _StreminiAgentScreenState extends ConsumerState<StreminiAgentScreen> {
  final TextEditingController _ownerController = TextEditingController();
  final TextEditingController _repoController = TextEditingController();
  final TextEditingController _taskController = TextEditingController();

  GithubAgentRunResult? _runResult;
  bool _isLoading = false;

  @override
  void dispose() {
    _ownerController.dispose();
    _repoController.dispose();
    _taskController.dispose();
    super.dispose();
  }

  Future<void> _runAgent() async {
    if (_ownerController.text.isEmpty || _repoController.text.isEmpty || _taskController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please fill in all fields'), backgroundColor: AppColors.warning),
      );
      return;
    }

    setState(() {
      _isLoading = true;
      _runResult = null;
    });

    try {
      final api = ref.read(apiServiceProvider);
      final result = await api.processGithubAgentTask(
        repoOwner: _ownerController.text.trim(),
        repoName: _repoController.text.trim(),
        task: _taskController.text.trim(),
      );

      setState(() {
        _runResult = result;
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'COMPLETED':
        return AppColors.success;
      case 'FIXED':
        return AppColors.info;
      default:
        return AppColors.danger;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.black,
      appBar: AppBar(
        backgroundColor: AppColors.black,
        elevation: 0,
        title: Text('Stremini GitHub Coder Agent', style: AppTextStyles.h2),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            AppContainer(
              width: double.infinity,
              padding: const EdgeInsets.all(18),
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  AppColors.primary.withOpacity(0.26),
                  AppColors.scanCyan.withOpacity(0.18),
                ],
              ),
              border: BorderSide(color: AppColors.scanCyan.withOpacity(0.35)),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Production-grade GitHub automation', style: AppTextStyles.h3),
                  const SizedBox(height: 8),
                  Text(
                    'The agent follows the CONTINUE loop contract, tracks read files, and stops safely after server-defined limits.',
                    style: AppTextStyles.body3.copyWith(color: AppColors.hintGray),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            _buildInputField(
              controller: _ownerController,
              label: 'Repository Owner',
              hint: 'e.g. your-github-username',
              icon: Icons.person_outline,
            ),
            const SizedBox(height: 14),
            _buildInputField(
              controller: _repoController,
              label: 'Repository Name',
              hint: 'e.g. your-repo-name',
              icon: Icons.folder_open_outlined,
            ),
            const SizedBox(height: 14),
            _buildInputField(
              controller: _taskController,
              label: 'Task for the Agent',
              hint: 'e.g. Investigate API loop handling and push a robust fix',
              icon: Icons.code_outlined,
              maxLines: 5,
            ),
            const SizedBox(height: 18),
            if (_isLoading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 20),
                  child: Column(
                    children: [
                      CircularProgressIndicator(color: AppColors.scanCyan),
                      SizedBox(height: 14),
                      Text('Analyzing repository and executing task loop...', style: TextStyle(color: AppColors.scanCyan)),
                    ],
                  ),
                ),
              ),
            if (_runResult != null) _buildResultPanel(_runResult!),
            const SizedBox(height: 96),
          ],
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _isLoading ? null : _runAgent,
        backgroundColor: AppColors.scanCyan,
        icon: const Icon(Icons.terminal),
        label: const Text('Run Agent', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
    );
  }

  Widget _buildInputField({
    required TextEditingController controller,
    required String label,
    required String hint,
    required IconData icon,
    int maxLines = 1,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: AppTextStyles.body2.copyWith(fontWeight: FontWeight.w700)),
        const SizedBox(height: 8),
        TextField(
          controller: controller,
          maxLines: maxLines,
          style: const TextStyle(color: AppColors.white),
          decoration: InputDecoration(
            hintText: hint,
            hintStyle: const TextStyle(color: AppColors.hintGray),
            prefixIcon: Icon(icon, color: AppColors.scanCyan, size: 20),
            filled: true,
            fillColor: AppColors.darkGray,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: AppColors.scanCyan.withOpacity(0.3)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: AppColors.scanCyan),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildResultPanel(GithubAgentRunResult result) {
    final statusColor = _statusColor(result.status);
    final durationMs = result.duration.inMilliseconds;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        AppContainer(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          color: AppColors.darkGray,
          border: BorderSide(color: statusColor.withOpacity(0.5)),
          child: Wrap(
            runSpacing: 8,
            spacing: 8,
            children: [
              _buildMetricChip('Status: ${result.status}', statusColor),
              _buildMetricChip('Iterations: ${result.iterationCount}', AppColors.info),
              _buildMetricChip('Files read: ${result.visitedFiles.length}', AppColors.emotional),
              _buildMetricChip('Duration: ${durationMs}ms', AppColors.warning),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _buildSectionHeader('Agent Output', result.summary),
        const SizedBox(height: 10),
        _buildCodeBox(result.summary),
        if (result.rawPayload.isNotEmpty) ...[
          const SizedBox(height: 14),
          _buildSectionHeader('Raw API Payload', 'Useful for debugging response contracts'),
          const SizedBox(height: 10),
          _buildCodeBox(result.rawPayload),
        ],
      ],
    );
  }

  Widget _buildMetricChip(String value, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(99),
        color: color.withOpacity(0.16),
        border: Border.all(color: color.withOpacity(0.4)),
      ),
      child: Text(value, style: const TextStyle(color: AppColors.white, fontWeight: FontWeight.w600)),
    );
  }

  Widget _buildSectionHeader(String title, String subtitle) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTextStyles.h3),
        const SizedBox(height: 4),
        Text(subtitle, style: AppTextStyles.body3.copyWith(color: AppColors.hintGray)),
      ],
    );
  }

  Widget _buildCodeBox(String text) {
    return AppContainer(
      width: double.infinity,
      color: AppColors.darkGray,
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          IconButton(
            icon: const Icon(Icons.copy_all, color: AppColors.scanCyan),
            onPressed: () {
              Clipboard.setData(ClipboardData(text: text));
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Copied to clipboard.')),
              );
            },
          ),
          SelectableText(
            text,
            style: const TextStyle(fontFamily: 'monospace', color: AppColors.white, fontSize: 12.8),
          ),
        ],
      ),
    );
  }
}
