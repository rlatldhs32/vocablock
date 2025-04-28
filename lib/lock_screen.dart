import 'dart:math'; // Import dart:math for Random
import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:speech_to_text/speech_recognition_result.dart';
import 'home_screen.dart'; // Import the HomeScreen

class LockScreen extends StatefulWidget {
  const LockScreen({super.key});

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> {
  // Simple list of words
  final List<String> _wordList = [
    'apple',
    'banana',
    'cherry',
    'grape',
    'lemon',
    'orange',
    'peach',
    'strawberry',
  ];

  String _currentWord = ""; // Initialize with empty string
  final Random _random = Random(); // Random number generator

  // Speech to Text objects and state
  late stt.SpeechToText _speech;
  bool _isListening = false;
  String _recognizedWords = '';
  bool _speechEnabled = false;
  String? _lastError;
  String? _lastStatus;

  @override
  void initState() {
    super.initState();
    _initSpeech();
    _setRandomWord(); // Set initial random word
  }

  /// This has to happen only once per app
  void _initSpeech() async {
    _speech = stt.SpeechToText(); // Initialize the speech instance
    _speechEnabled = await _speech.initialize(
      onError: _errorListener,
      onStatus: _statusListener,
      // debugLog: true, // Uncomment for debugging
    );
    if (mounted) {
      setState(() {});
    }
    print("Speech recognition initialized: $_speechEnabled");
    // NOTE: Ensure AndroidManifest.xml and Info.plist permissions are set!
  }

  void _navigateToHome() {
    // Use pushReplacement to prevent going back to the lock screen
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (context) => const HomeScreen()),
    );
  }

  // Function to select and set a random word (now only used for initialization)
  void _setRandomWord() {
    setState(() {
      _currentWord = _wordList[_random.nextInt(_wordList.length)];
      _recognizedWords = '';
    });
  }

  void _emergencyUnlock() {
    if (_isListening) {
      _stopListening();
    }
    print("Emergency Unlock Tapped!");
    // _setRandomWord(); // Remove this
    _navigateToHome(); // Navigate to home screen
  }

  // Start listening function
  void _startListening() {
    if (!_speechEnabled || _isListening) return;
    _lastError = null;
    _recognizedWords = ''; // Clear previous recognition results
    // The listen function launches the listening process
    _speech.listen(
        onResult: _resultListener,
        listenFor: const Duration(seconds: 5), // Adjust listening duration
        localeId: 'en_US', // Set locale for English
        // onSoundLevelChange: _soundLevelListener, // Optional: for mic level feedback
        cancelOnError: true,
        partialResults: false, // We only need the final result
        listenMode:
            stt.ListenMode.confirmation // Mode confirmation for single phrase
        );
    setState(() {
      _isListening = true;
    });
  }

  // Stop listening function
  void _stopListening() {
    if (!_isListening) return;
    _speech.stop();
    setState(() {
      _isListening = false;
    });
  }

  /// Speech recognition result callback
  void _resultListener(SpeechRecognitionResult result) {
    // Temporary variable to hold recognized words for immediate check
    String recognized = result.recognizedWords.toLowerCase();

    setState(() {
      _recognizedWords = recognized; // Update state for UI display
      print("Recognized: $_recognizedWords");

      // Check if the recognized word matches the current word AND it's the final result
      if (!_isListening &&
          result.finalResult &&
          recognized == _currentWord.toLowerCase()) {
        print("Correct word spoken! Unlocking...");
        // _setRandomWord(); // Remove this
        _navigateToHome(); // Navigate to home screen
      }
    });
  }

  /// Speech recognition error callback
  void _errorListener(dynamic error) {
    setState(() {
      _lastError = 'Error: ${error.errorMsg} - ${error.permanent}';
      _isListening = false;
      print(_lastError);
    });
  }

  /// Speech recognition status callback
  void _statusListener(String status) {
    setState(() {
      _lastStatus = status;
      // Automatically stop listening when speech recognition is done
      if (status == stt.SpeechToText.doneStatus ||
          status == stt.SpeechToText.notListeningStatus) {
        _isListening = false;
      }
      print("Status: $status, Listening: $_isListening");
    });
  }

  // Toggle listening state - replaces _listenAndVerify
  void _toggleListening() {
    if (!_speechEnabled) {
      print(
          "Speech recognition not enabled. Check permissions and initialization.");
      // Optionally show a message to the user
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text(
                'Speech recognition is not available. Please check permissions.')),
      );
      return;
    }

    if (_isListening) {
      _stopListening();
    } else {
      _startListening();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        // Use Stack to layer the unlock button
        children: [
          // Main content area (word display and voice input trigger)
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: <Widget>[
                // Word Display
                Text(
                  _currentWord.isEmpty
                      ? "Loading..."
                      : _currentWord, // Show loading or word
                  style: Theme.of(context).textTheme.headlineLarge?.copyWith(
                        fontSize: 48.0,
                      ), // Larger font size
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 20),
                // Display recognized text (optional for debugging/feedback)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                  child: Text(
                    _isListening
                        ? "Listening..."
                        : (_recognizedWords.isNotEmpty
                            ? _recognizedWords
                            : "Tap mic to speak"),
                    style: Theme.of(context).textTheme.titleMedium,
                    textAlign: TextAlign.center,
                  ),
                ),
                // Display error message if any
                if (_lastError != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8.0),
                    child: Text(
                      _lastError!,
                      style: const TextStyle(color: Colors.red, fontSize: 12),
                      textAlign: TextAlign.center,
                    ),
                  ),
                const SizedBox(height: 40), // Spacing
                // Voice Input Trigger Button
                ElevatedButton.icon(
                  icon: Icon(_isListening
                      ? Icons.mic_off
                      : Icons.mic), // Change icon based on state
                  label: Text(_isListening
                      ? 'Stop'
                      : 'Speak'), // Change label based on state
                  onPressed: _toggleListening, // Use the new toggle function
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 30, vertical: 15),
                    textStyle: const TextStyle(fontSize: 18),
                    // Change color when listening
                    backgroundColor: _isListening
                        ? Colors.red
                        : Theme.of(context).primaryColor,
                    foregroundColor: Colors.white,
                  ),
                ),
              ],
            ),
          ),

          // Emergency Unlock Button (positioned at the bottom right)
          Positioned(
            bottom: 30,
            right: 30,
            child: ElevatedButton(
              onPressed: _emergencyUnlock,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.redAccent, // Make it distinct
                foregroundColor: Colors.white,
                shape: const CircleBorder(), // Circular button
                padding: const EdgeInsets.all(
                  20,
                ), // Increase padding for larger tap area
              ),
              child: const Icon(Icons.lock_open), // Icon for unlock
            ),
          ),
        ],
      ),
    );
  }
}
