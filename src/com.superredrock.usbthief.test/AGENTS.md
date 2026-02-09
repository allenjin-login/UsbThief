# Test Package - Manual Test Stubs

**Package:** `com.superredrock.usbthief.test`
**Files:** 9 Java classes
**Type:** Manual test execution (main() methods only)

## OVERVIEW
Manual test suite for UsbThief components. No testing framework (JUnit/TestNG) - all tests use main() methods for direct execution.

## STRUCTURE
```
com.superredrock.usbthief.test/
├── ConcurrencyTest.java           # Thread-safety tests
├── DeviceScannerTest.java         # WatchService file monitoring tests
├── FileSystemTest.java            # File system operations tests
├── LoadEvaluatorTest.java         # Load calculation verification
├── SpeedProbeGroupTest.java       # Speed probe aggregation tests
├── SpeedProbeTest.java            # Individual speed probe tests
├── StatsPanelTest.java            # GUI component tests
└── TaskSchedulerTest.java         # Task scheduling logic tests
```

## WHERE TO LOOK
| Test Target | Test File | Notes |
|-------------|-----------|-------|
| File system basics | FileSystemTest.java | Path operations, file creation |
| WatchService | DeviceScannerTest.java | File change detection |
| Concurrency | ConcurrencyTest.java | Thread pool, synchronization |
| Task scheduling | TaskSchedulerTest.java | Priority dispatch, load control |
| Speed monitoring | SpeedProbeTest.java, SpeedProbeGroupTest.java | Real-time speed tracking |
| Load evaluation | LoadEvaluatorTest.java | System load calculation |
| GUI components | StatsPanelTest.java | Swing UI testing |

## CONVENTIONS
- **Test execution**: Direct `java -p out -m com.superredrock.usbthief.test/ClassName` command
- **No assertions**: Manual verification via console output
- **Standalone**: Each test is self-contained with its own main()
- **No dependencies**: Avoid JUnit/TestNG - not configured in this project

## ANTI-PATTERNS (THIS PACKAGE)
- **DO NOT add JUnit/TestNG** - framework not configured, manual tests only
- **DO NOT use @Test annotations** - tests use main() methods
- **DO NOT expect automated execution** - run tests manually as needed

## COMMANDS
```bash
# Compile a test
javac -d out src/com.superredrock.usbthief.test/FileSystemTest.java --module-source-path src

# Run a test
java -p out -m com.superredrock.usbthief.test/com.superredrock.usbthief.test.FileSystemTest

# Compile all tests
javac -d out --module-source-path src -m com.superredrock.usbthief.test
```

## NOTES
- **Pre-existing issue**: Package declaration mismatch in module-info.java (see LSP diagnostics)
- **Manual verification required**: Tests print results, no pass/fail automation
- **Use for**: Exploratory testing, debugging, verification of specific components
