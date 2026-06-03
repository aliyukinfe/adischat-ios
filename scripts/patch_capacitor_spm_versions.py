from pathlib import Path


roots = [
    Path("node_modules/@capacitor-community"),
    Path("node_modules/@capacitor"),
    Path("node_modules/@capacitor-firebase"),
]

patched = 0
checked = 0

for root in roots:
    if not root.exists():
        continue

    for package_file in root.glob("*/Package.swift"):
        checked += 1
        content = package_file.read_text(encoding="utf-8")
        updated = content.replace(
            'capacitor-swift-pm.git", from: "7.0.0"',
            'capacitor-swift-pm.git", from: "8.0.0"',
        )

        if updated != content:
            package_file.write_text(updated, encoding="utf-8")
            patched += 1
            print(f"Patched Capacitor SwiftPM version in {package_file}")

print(f"Checked {checked} plugin Package.swift files; patched {patched}.")
