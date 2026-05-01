@echo off
rem ─────────────────────────────────────────────────────────────────────────────
rem DailyLearn Android — one-time Windows setup script
rem
rem Run this ONCE after unzipping the project, before your first build.
rem It downloads gradle-wrapper.jar and verifies your environment.
rem
rem Usage:  Double-click setup.bat  OR  run from Command Prompt
rem ─────────────────────────────────────────────────────────────────────────────

setlocal

set WRAPPER_DIR=%~dp0gradle\wrapper
set JAR_PATH=%WRAPPER_DIR%\gradle-wrapper.jar
set GRADLE_VERSION=8.4
set JAR_URL=https://raw.githubusercontent.com/gradle/gradle/v%GRADLE_VERSION%.0/gradle/wrapper/gradle-wrapper.jar

echo =^> Checking for gradle-wrapper.jar ...

if exist "%JAR_PATH%" (
    for %%A in ("%JAR_PATH%") do if %%~zA GTR 1000 (
        echo     Already present -- skipping download.
        goto :make_executable
    )
)

echo     Downloading gradle-wrapper.jar for Gradle %GRADLE_VERSION% ...

where curl >nul 2>&1
if %ERRORLEVEL% equ 0 (
    curl -fL -o "%JAR_PATH%" "%JAR_URL%"
    goto :check_size
)

where powershell >nul 2>&1
if %ERRORLEVEL% equ 0 (
    powershell -Command "Invoke-WebRequest -Uri '%JAR_URL%' -OutFile '%JAR_PATH%'"
    goto :check_size
)

:no_download
echo.
echo  +----------------------------------------------------------+
echo  ^|  Could not download gradle-wrapper.jar automatically.   ^|
echo  ^|                                                          ^|
echo  ^|  Recommended: Open the project in Android Studio --     ^|
echo  ^|  it handles Gradle for you automatically.               ^|
echo  ^|                                                          ^|
echo  ^|  Or install Gradle globally and run:                    ^|
echo  ^|    gradle wrapper --gradle-version 8.4                  ^|
echo  +----------------------------------------------------------+
pause
exit /b 1

:check_size
for %%A in ("%JAR_PATH%") do if %%~zA LSS 1000 (
    echo     Download may have returned a placeholder. Trying 'gradle wrapper' ...
    where gradle >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        cd /d "%~dp0"
        gradle wrapper --gradle-version %GRADLE_VERSION%
    ) else (
        goto :no_download
    )
)

:make_executable
echo =^> Ready! Build with:
echo      gradlew.bat assembleDebug
echo.
echo    APK will be at:
echo      app\build\outputs\apk\debug\app-debug.apk
echo.
pause
