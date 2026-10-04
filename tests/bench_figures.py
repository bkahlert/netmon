"""The benchmark's figures: one run's samples cut into phases, their CPU, memory, faults and settle time."""
from dataclasses import dataclass

from sampling import Sample


@dataclass(frozen=True)
class Timeline:
    """One run's samples: the last before the kiosk's restart, those of the restart, and from t0 to the end with the index each scan followed."""

    before: Sample
    load: list[Sample]
    samples: list[Sample]
    boundaries: list[int]
