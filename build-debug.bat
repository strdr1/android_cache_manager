@echo off
setlocal
cd /d "%~dp0"
if exist gradlew.bat (
  call gradlew.bat :app:assembleDebug --stacktrace --warning-mode all
) else (
  echo gradlew.bat not found
  exit /b 1
)
if exist "app\build\outputs\apk\debug\app-debug.apk" (
  echo APK ready: app\build\outputs\apk\debug\app-debug.apk
) else (
  echo APK not found. Check build output.
  exit /b 2
)
endlocal
