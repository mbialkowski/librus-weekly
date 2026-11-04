#!/bin/bash
# Archives the app and uploads it to App Store Connect for TestFlight.
# Needs Xcode signed in to your developer account and Config/Signing.xcconfig filled in.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v xcodegen >/dev/null || { echo "Install XcodeGen first: brew install xcodegen"; exit 1; }
xcodegen generate --quiet

# Every upload needs a new, higher build number.
BUILD_NUMBER="$(date +%Y%m%d%H%M)"
rm -rf build/LibrusSummary.xcarchive build/export

xcodebuild \
  -project LibrusSummary.xcodeproj \
  -scheme LibrusSummary \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/LibrusSummary.xcarchive \
  -allowProvisioningUpdates \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER" \
  archive

xcodebuild \
  -exportArchive \
  -archivePath build/LibrusSummary.xcarchive \
  -exportOptionsPlist scripts/ExportOptions.plist \
  -exportPath build/export \
  -allowProvisioningUpdates

echo "Uploaded build $BUILD_NUMBER. It shows up in App Store Connect > TestFlight after processing."
