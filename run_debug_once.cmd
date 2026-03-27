@echo off
setlocal enabledelayedexpansion

chcp 65001 >nul

set "PROJECT_DIR=C:\Users\pc\Documents\26年项目管理\01 Android开发\VoiceToText"
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

set "LOG_DIR=%PROJECT_DIR%\debug-logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
set "TS=%DATE:~0,4%%DATE:~5,2%%DATE:~8,2%_%TIME:~0,2%%TIME:~3,2%%TIME:~6,2%"
set "TS=%TS: =0%"
set "BUILD_LOG=%LOG_DIR%\build_install_%TS%.log"
set "CRASH_LOG=%LOG_DIR%\crash_%TS%.log"
set "MAIN_LOG=%LOG_DIR%\main_%TS%.log"

echo [1/6] Enter project dir...
cd /d "%PROJECT_DIR%" || (
  echo Failed to enter project dir: "%PROJECT_DIR%"
  exit /b 1
)

echo [2/6] Start ADB...
adb start-server
echo [3/6] Check connected devices...
adb devices

for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
  if "%%b"=="device" set "HAS_DEVICE=1"
)
if not defined HAS_DEVICE (
  echo No device connected. Please enable USB debugging first.
  exit /b 2
)

echo [4/6] Build and install debug apk...
call gradlew.bat clean installDebug > "%BUILD_LOG%" 2>&1
if errorlevel 1 (
  echo Build/install failed. See log:
  echo %BUILD_LOG%
  type "%BUILD_LOG%"
  exit /b 3
)

echo [5/6] Launch app...
adb logcat -c >nul 2>&1
adb shell monkey -p com.rabbit.voicetotext -c android.intent.category.LAUNCHER 1 >nul 2>&1

echo [6/6] Collect logs...
adb logcat -d -b crash > "%CRASH_LOG%" 2>&1
adb logcat -d -v time AndroidRuntime:E *:S > "%MAIN_LOG%" 2>&1

echo.
echo Done.
echo Build log: %BUILD_LOG%
echo Crash log: %CRASH_LOG%
echo Main log : %MAIN_LOG%
echo.
echo Last 30 lines of crash log:
powershell -NoProfile -Command "Get-Content -Path '%CRASH_LOG%' -Tail 30"

exit /b 0
