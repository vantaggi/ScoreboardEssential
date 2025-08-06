import 'package:flutter/material.dart';

void main() {
  runApp(const ScoreboardApp());
}

class ScoreboardApp extends StatelessWidget {
  const ScoreboardApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Scoreboard',
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
        brightness: Brightness.dark,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: const ScoreboardHomePage(),
    );
  }
}

class ScoreboardHomePage extends StatelessWidget {
  const ScoreboardHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scoreboard'),
      ),
      body: const Center(
        child: Text('Mobile App'),
      ),
    );
  }
}

// TODO: Implement the Wear OS app
class WearOsApp extends StatelessWidget {
  const WearOsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      home: Scaffold(
        body: Center(
          child: Text('Wear OS App'),
        ),
      ),
    );
  }
}
