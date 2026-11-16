# Seller Application Access and Field Validation

Date: 2026-07-29

## Android

- Unapproved applicants see only the seller application experience.
- Seller dashboard, orders, products, requests, messages and more navigation unlock after approval.
- Suspended sellers retain access to historical seller information while mutations remain server-restricted.
- Business type is a required controlled choice: Individual or PLC.
- TIN accepts digits only and requires exactly 10 digits.
- Region is fixed to Addis Ababa City.
- District/subcity is a required controlled choice from the eleven Addis Ababa subcities.
- Proof of address and authorization uploads were removed from the application UI.

## Server

- Submission validates business type, TIN, region and subcity independently of Android.
- New proof-of-address and authorization document uploads are rejected.
- Historical documents were preserved for audit integrity; no production data was deleted.
- No database migration was required.

## Deployment

- `adishop-get-seller-application-status`: version 8, active
- `adishop-submit-seller-application`: version 9, active
- `adishop-seller-save-application-draft`: version 3, active
- `adishop-seller-submit-application`: version 4, active
- `adishop-seller-upload-application-document`: version 3, active
- Missing-session probes returned HTTP 401 for every affected route.

## Remaining Validation Blocker

The Android Kotlin compilation still exceeds the five-minute diagnostic limit under the required 3 GB Gradle heap. The Android changes have not been installed on the connected device and must not be treated as release-validated.
