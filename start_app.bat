@echo off
setlocal

cd /d "%~dp0"

set "JAVA_HOME=C:\Users\Lenovo\.jdks\jdk-17.0.19+10"
set "ANDROID_HOME=C:\Users\Lenovo\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"

echo [1/4] Building debug APK...
call gradlew.bat --console=plain :app:assembleDebug
if errorlevel 1 goto failed

if not exist "%ADB%" (
  echo Android adb not found: %ADB%
  echo APK has been built at app\build\outputs\apk\debug\app-debug.apk
  goto done
)

echo [2/4] Checking connected Android device or emulator...
"%ADB%" get-state >nul 2>nul
if errorlevel 1 (
  echo No Android device or emulator is connected.
  echo Open an emulator in Android Studio, then run start_app.bat again.
  echo APK has been built at app\build\outputs\apk\debug\app-debug.apk
  goto done
)

echo [3/4] Installing APK...
"%ADB%" install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 goto failed

echo [4/4] Launching app...
"%ADB%" shell monkey -p edu.whu.tmdb 1
if errorlevel 1 goto failed

echo App launched successfully.
goto done

:failed
echo.
echo Failed. Check the output above.
exit /b 1

:done
endlocal
