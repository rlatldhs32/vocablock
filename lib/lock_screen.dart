import 'dart:math'; // Import dart:math for Random
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:flutter_tts/flutter_tts.dart'; // Import for text-to-speech

// Use the same method channel from main.dart
const platformChannel = MethodChannel('sion.vocablock.vocablock/lockscreen');

class LockScreen extends StatefulWidget {
  const LockScreen({super.key});

  @override
  State<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends State<LockScreen> with WidgetsBindingObserver {
  // TOEFL vocabulary words with Korean meanings
  final List<Map<String, String>> _wordList = [
    {'word': 'abate', 'meaning': '감소하다, 약화되다'},
    {'word': 'aberrant', 'meaning': '정상에서 벗어난, 변칙적인'},
    {'word': 'abeyance', 'meaning': '중지 상태, 보류'},
    {'word': 'abscond', 'meaning': '도주하다, 도망가다'},
    {'word': 'abstain', 'meaning': '삼가다, 절제하다'},
    {'word': 'acumen', 'meaning': '예리한 통찰력, 영리함'},
    {'word': 'admonish', 'meaning': '훈계하다, 경고하다'},
    {'word': 'ambivalent', 'meaning': '양면적인, 상반된 감정의'},
    {'word': 'ameliorate', 'meaning': '개선하다, 향상시키다'},
    {'word': 'anomaly', 'meaning': '변칙, 이례, 기형'},
    {'word': 'apathy', 'meaning': '무관심, 냉담'},
    {'word': 'arbitrary', 'meaning': '임의의, 전제적인'},
    {'word': 'arcane', 'meaning': '비밀의, 신비한'},
    {'word': 'arduous', 'meaning': '힘든, 어려운'},
    {'word': 'articulate', 'meaning': '명확하게 표현하다, 분명히 발음하다'},
    {'word': 'ascertain', 'meaning': '확인하다, 알아내다'},
    {'word': 'audacious', 'meaning': '대담한, 무모한'},
    {'word': 'augment', 'meaning': '증가시키다, 확대하다'},
    {'word': 'aversion', 'meaning': '혐오, 기피'},
    {'word': 'benevolent', 'meaning': '자비로운, 친절한'},
    {'word': 'cacophony', 'meaning': '불협화음, 소음'},
    {'word': 'capricious', 'meaning': '변덕스러운, 예측할 수 없는'},
    {'word': 'cogent', 'meaning': '설득력 있는, 타당한'},
    {'word': 'deference', 'meaning': '존중, 경의'},
    {'word': 'disparate', 'meaning': '전혀 다른, 이질적인'},
  ];

  String _currentWord = ""; // Initialize with empty string
  String _currentMeaning = ""; // Current Korean meaning
  final Random _random = Random(); // Random number generator

  // Speech to Text objects and state
  late stt.SpeechToText _speech;
  bool _isListening = false;
  String _recognizedWords = '';
  bool _speechEnabled = false;
  String? _lastError;
  String? _lastStatus;

  // Text to Speech
  final FlutterTts _flutterTts = FlutterTts();
  bool _isSpeaking = false;

  @override
  void initState() {
    super.initState();
    // Register lifecycle observer
    WidgetsBinding.instance.addObserver(this);
    // Handle 'newWord' event from native side to randomize the displayed word and start listening
    platformChannel.setMethodCallHandler((call) async {
      if (call.method == 'newWord') {
        _setRandomWord();
      }
    });
    _initSpeech();
    _initTts();
    _setRandomWord(); // Set initial random word
  }

  @override
  void dispose() {
    // Unregister lifecycle observer
    WidgetsBinding.instance.removeObserver(this);
    _flutterTts.stop();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    if (state == AppLifecycleState.resumed) {
      // Each time app resumes (e.g., after screen on), pick a new word and start listening
      _setRandomWord();
    }
  }

  // Initialize text-to-speech
  void _initTts() async {
    await _flutterTts.setLanguage("en-US");
    await _flutterTts.setSpeechRate(0.5); // Slower speech rate for learning
    await _flutterTts.setVolume(1.0);
    await _flutterTts.setPitch(1.0);

    _flutterTts.setCompletionHandler(() {
      setState(() {
        _isSpeaking = false;
      });
    });
  }

  // Pronounce the current word
  Future<void> _speakWord() async {
    if (_isSpeaking) {
      await _flutterTts.stop();
      setState(() {
        _isSpeaking = false;
      });
      return;
    }

    if (_currentWord.isNotEmpty) {
      setState(() {
        _isSpeaking = true;
      });
      await _flutterTts.speak(_currentWord);
    }
  }

  // Function to select and set a random word
  void _setRandomWord() {
    final randomIndex = _random.nextInt(_wordList.length);
    setState(() {
      _currentWord = _wordList[randomIndex]['word']!;
      _currentMeaning = _wordList[randomIndex]['meaning']!;
      _recognizedWords = ''; // Clear recognized words for the new word
    });
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

  void _unlockDevice() async {
    // First try to use native unlock through method channel
    try {
      await platformChannel.invokeMethod('unlockDevice');
      // If successful, we'll exit the app
      SystemNavigator.pop(); // This will exit the app
    } catch (e) {
      print("Error unlocking device: $e");
      // Try another way to exit/minimize the app
      try {
        await platformChannel.invokeMethod('minimizeApp');
      } catch (e) {
        print("Error minimizing app: $e");
        // As a last resort, just pick a new word and continue
        _setRandomWord();
      }
    }
  }

  void _emergencyUnlock() {
    if (_isListening) {
      _stopListening();
    }
    print("Emergency Unlock Tapped!");
    _unlockDevice();
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

      // Check if first letter of recognized word matches first letter of current word when it's the final result
      if (result.finalResult &&
          recognized.isNotEmpty &&
          _currentWord.isNotEmpty &&
          recognized[0] == _currentWord.toLowerCase()[0]) {
        print("First letter matches! Unlocking...");
        _unlockDevice();
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
                // Word Display with Pronunciation Button
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Text(
                      _currentWord.isEmpty
                          ? "Loading..."
                          : _currentWord, // Show loading or word
                      style:
                          Theme.of(context).textTheme.headlineLarge?.copyWith(
                                fontSize: 48.0,
                              ), // Larger font size
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(width: 10),
                    IconButton(
                      icon: Icon(
                        _isSpeaking
                            ? Icons.volume_up
                            : Icons.volume_up_outlined,
                        color: _isSpeaking ? Colors.blue : Colors.grey,
                        size: 30,
                      ),
                      onPressed: _speakWord,
                      tooltip: '발음 듣기',
                    ),
                  ],
                ),
                // Korean Meaning Display
                Text(
                  _currentMeaning.isEmpty ? "" : _currentMeaning,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                        fontSize: 20.0,
                        color: Colors.grey[700],
                      ),
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
