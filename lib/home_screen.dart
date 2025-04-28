import 'package:flutter/material.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('VocabLock Home'),
        automaticallyImplyLeading:
            false, // Don't show back button to lock screen
      ),
      body: const Center(
        child: Text(
          'Welcome! Screen Unlocked.',
          style: TextStyle(fontSize: 24),
        ),
      ),
    );
  }
}
