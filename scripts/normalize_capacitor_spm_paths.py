from pathlib import Path


package_file = Path("ios/App/CapApp-SPM/Package.swift")

if not package_file.exists():
    raise SystemExit(f"{package_file} does not exist")

content = package_file.read_text(encoding="utf-8")
normalized = content.replace("\\", "/")

if normalized != content:
    package_file.write_text(normalized, encoding="utf-8")
    print(f"Normalized SwiftPM paths in {package_file}")
else:
    print(f"SwiftPM paths already normalized in {package_file}")
