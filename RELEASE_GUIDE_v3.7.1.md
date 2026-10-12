# SuperJet Staff v3.7.1 — Full Consolidated Release

This release package contains the complete Android project source, a freshly built Android APK, the current SuperJet server source snapshot, and this guide. It is a full application build, not an incremental APK patch.

## What's included
- Manager-only statistics tab.
- Separate measures for website booking requests, employee operation records, issued-ticket records, rejected operations, requested seats, and seats in issued tickets.
- Today, current-month, all-time, and per-employee views.
- Existing staff workflows retained from the Android project: login, booking operations, payment review, notification reading/sync, and staff chat.

## Important server dependency
The manager statistics tab calls `GET /api/mobile/manager-statistics`. The matching endpoint is included in the bundled server source under `webbooking/staff_portal.py`.

The APK cannot update a running Windows server by itself. Deploy the matching server code to the machine running SuperJet, while preserving its existing `data` directory/database, environment variables, service configuration, and payment credentials. Then restart the existing service and verify the endpoint using a manager account.

## Install
1. Download `SuperJet_Staff_v3.7.1_FULL_CONSOLIDATED.apk` and install it on the Android phone.
2. Sign in with the employee's existing SuperJet credentials.
3. On the payment-notification tab, enable Android Notification Access if payment syncing is needed.
4. Sign in with a manager account to see the Manager Statistics tab.

## Update/signature note
This build workflow currently builds the Android `debug` variant. GitHub-hosted runners do not preserve a stable debug-signing key between independent runs, so Android may reject an install over a previous APK with a different signature. If that happens, back up anything needed first, uninstall the older app, then install this APK and sign in again. A durable in-place upgrade requires configuring a private release-signing keystore in GitHub Actions secrets; do not commit that keystore to a public repository.

## Verification
The workflow checks Python syntax for the backend files, runs Android unit-test tasks, assembles the APK, verifies its APK signature, packages both source trees, and records SHA-256 checksums. These checks reduce build risk but do not replace live testing against the actual SuperJet database and Android device.
