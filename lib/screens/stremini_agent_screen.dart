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
  String _resultCode = "";
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
      _resultCode = "";
    });

    try {
      final api = ref.read(apiServiceProvider);
      final response = await api.processGithubAgentTask(
        repoOwner: _ownerController.text.trim(),
        repoName: _repoController.text.trim(),
        task: _taskController.text.trim(),
      );

      setState(() {
        _resultCode = response;
      });
    } catch (e) {
      setState(() {
        _resultCode = "Error: $e";
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.black,
      appBar: AppBar(
        backgroundColor: AppColors.black,
        elevation: 0,
        title: Text('Stremini: GitHub Architect', style: AppTextStyles.h2),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Autonomous Repository Debugger', style: AppTextStyles.subtitle1.copyWith(color: AppColors.scanCyan)),
            const SizedBox(height: 24),
            
            _buildInputField(
              controller: _ownerController,
              label: 'GitHub Username',
              hint: 'e.g. 983111',
              icon: Icons.person_outline,
            ),
            const SizedBox(height: 16),
            
            _buildInputField(
              controller: _repoController,
              label: 'Repository Name',
              hint: 'e.g. Streminiai-',
              icon: Icons.folder_open_outlined,
            ),
            const SizedBox(height: 16),
            
            _buildInputField(
              controller: _taskController,
              label: 'Coding Task',
              hint: 'e.g. Add a dark theme toggle or fix the API error',
              icon: Icons.code_outlined,
              maxLines: 4,
            ),
            const SizedBox(height: 24),

            if (_isLoading)
              const Center(
                child: Column(
                  children: [
                    CircularProgressIndicator(color: AppColors.scanCyan),
                    SizedBox(height: 16),
                    Text('Agent is reasoning through the repo tree...', 
                        style: TextStyle(color: AppColors.scanCyan, fontStyle: FontStyle.italic)),
                  ],
                ),
              )
            else if (_resultCode.isNotEmpty)
              _buildCodeResult(),
            
            const SizedBox(height: 100), // Space for FAB
          ],
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _isLoading ? null : _runAgent,
        backgroundColor: AppColors.scanCyan,
        icon: const Icon(Icons.auto_fix_high),
        label: const Text('Start Reasoning', style: TextStyle(fontWeight: FontWeight.bold)),
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
        Text(label, style: AppTextStyles.body2.copyWith(fontWeight: FontWeight.bold)),
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

  Widget _buildCodeResult() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('Suggested Code Solution', style: AppTextStyles.h3),
            IconButton(
              icon: const Icon(Icons.copy_all, color: AppColors.scanCyan),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: _resultCode));
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Code copied to clipboard!')),
                );
              },
            ),
          ],
        ),
        const SizedBox(height: 12),
        AppContainer(
          width: double.infinity,
          color: AppColors.darkGray,
          padding: const EdgeInsets.all(16),
          child: SelectableText(
            _resultCode,
            style: const TextStyle(
              fontFamily: 'monospace',
              color: AppColors.white,
              fontSize: 13,
            ),
          ),
        ),
      ],
    );
  }
}
