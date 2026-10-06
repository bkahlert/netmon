from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def production_kotlin_sources():
    return sorted(
        path
        for source_set in ("commonMain", "jvmMain", "jsMain")
        for path in (ROOT / "src" / source_set / "kotlin").rglob("*.kt")
    )
