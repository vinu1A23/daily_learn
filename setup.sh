#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# DailyLearn Android — one-time setup script
#
# Run this ONCE after unzipping the project, before your first build.
# It downloads the gradle-wrapper.jar (≈63 KB) from the official Gradle repo
# and makes gradlew executable.
#
# Usage:
#   chmod +x setup.sh && ./setup.sh
# ─────────────────────────────────────────────────────────────────────────────

set -e

WRAPPER_DIR="$(dirname "$0")/gradle/wrapper"
JAR_PATH="$WRAPPER_DIR/gradle-wrapper.jar"
GRADLE_VERSION="8.4"
JAR_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar"
# Fallback: official Gradle services (requires access to services.gradle.org)
FALLBACK_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

echo "==> Checking for gradle-wrapper.jar ..."

if [ -f "$JAR_PATH" ] && [ "$(wc -c < "$JAR_PATH")" -gt 1000 ]; then
    echo "    Already present — skipping download."
else
    echo "    Downloading gradle-wrapper.jar for Gradle ${GRADLE_VERSION} ..."
    mkdir -p "$WRAPPER_DIR"

    # Try curl first
    if command -v curl >/dev/null 2>&1; then
        curl -fL -o "$JAR_PATH" "$JAR_URL" 2>/dev/null || \
        curl -fL -o "$JAR_PATH" \
          "https://github.com/gradle/gradle/releases/download/v${GRADLE_VERSION}.0/gradle-${GRADLE_VERSION}-wrapper.jar" 2>/dev/null || true
    fi

    # If jar still missing or tiny (LFS pointer), try wget
    if [ ! -f "$JAR_PATH" ] || [ "$(wc -c < "$JAR_PATH")" -lt 1000 ]; then
        if command -v wget >/dev/null 2>&1; then
            wget -q -O "$JAR_PATH" "$JAR_URL" 2>/dev/null || true
        fi
    fi

    # Last resort: use gradle (if installed globally) to regenerate the wrapper
    if [ ! -f "$JAR_PATH" ] || [ "$(wc -c < "$JAR_PATH")" -lt 1000 ]; then
        if command -v gradle >/dev/null 2>&1; then
            echo "    curl/wget unavailable or returned LFS stub."
            echo "    Using global 'gradle wrapper' to generate wrapper files ..."
            cd "$(dirname "$0")"
            gradle wrapper --gradle-version "$GRADLE_VERSION"
            echo "    Done via 'gradle wrapper'."
        else
            echo ""
            echo "  ┌──────────────────────────────────────────────────────────┐"
            echo "  │  Could not download gradle-wrapper.jar automatically.    │"
            echo "  │                                                          │"
            echo "  │  Option 1 (recommended): Open the project in            │"
            echo "  │    Android Studio — it handles Gradle automatically.    │"
            echo "  │                                                          │"
            echo "  │  Option 2: Install Gradle globally, then run:           │"
            echo "  │    gradle wrapper --gradle-version 8.4                  │"
            echo "  │                                                          │"
            echo "  │  Option 3: Download the jar manually:                   │"
            echo "  │    https://services.gradle.org/distributions/           │"
            echo "  │    gradle-8.4-bin.zip  → extract:                       │"
            echo "  │    gradle-8.4/lib/plugins/gradle-wrapper-8.4.jar        │"
            echo "  │    Copy it to: gradle/wrapper/gradle-wrapper.jar        │"
            echo "  └──────────────────────────────────────────────────────────┘"
            exit 1
        fi
    fi

    echo "    ✓ gradle-wrapper.jar downloaded."
fi

# Make gradlew executable
chmod +x "$(dirname "$0")/gradlew"
echo "==> ✓ gradlew is executable."
echo ""
echo "==> Ready! Build with:"
echo "      ./gradlew assembleDebug"
echo ""
echo "    APK will be at:"
echo "      app/build/outputs/apk/debug/app-debug.apk"
