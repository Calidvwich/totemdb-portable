@echo off
setlocal

cd /d "%~dp0"

set "JAVA_HOME=C:\Users\Lenovo\.jdks\jdk-17.0.19+10"
set "ANDROID_HOME=C:\Users\Lenovo\AppData\Local\Android\Sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

echo [1/3] Running regression tests...
call gradlew.bat --console=plain :app:runRegressionTest
if errorlevel 1 goto failed

echo [2/3] Running extreme input tests...
call gradlew.bat --console=plain :app:runExtremeInputTest
if errorlevel 1 goto failed

echo [3/3] Building debug APK...
call gradlew.bat --console=plain :app:assembleDebug
if errorlevel 1 goto failed

echo.
echo All checks passed.
goto done

:failed
echo.
echo Checks failed. Check the output above.
exit /b 1

:done
endlocal
