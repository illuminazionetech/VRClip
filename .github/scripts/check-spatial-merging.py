#!/usr/bin/env python3
"""Fails when R8 merged a Meta Spatial SDK class into another class.

The Spatial SDK registers systems and features by their class and refuses two of the same class,
so classes that R8's class merging folds together crash the immersive player at launch ("ScaleSystem
already registered ... Duplicate Systems are not allowed"). app/proguard-rules.pro keeps them apart;
this check reads the R8 mapping of a release build and proves it.

A merged class leaves no entry of its own in the mapping, while its constructor shows up, under
its original name, among the members of the class it was merged into.

Usage: check-spatial-merging.py app/build/outputs/mapping/<variant>/mapping.txt [...]
"""

from __future__ import annotations

import re
import sys
from collections import defaultdict

PREFIX = "com.meta.spatial."
CONSTRUCTOR = re.compile(r"^\s+(?:\d+:\d+:)?\S+ ([\w.$]+)\.<init>\(.*-> <init>$")


def merged_classes(path: str) -> dict[str, list[str]]:
    classes: set[str] = set()
    absorbed: dict[str, set[str]] = defaultdict(set)
    current = None
    with open(path, encoding="utf-8") as mapping:
        for line in mapping:
            if line.startswith("#"):
                continue
            if not line.startswith(" "):
                current = line.split(" -> ", 1)[0]
                classes.add(current)
                continue
            match = CONSTRUCTOR.match(line.rstrip("\n"))
            if match and current and match.group(1) != current:
                absorbed[current].add(match.group(1))
    result = {}
    for target, sources in absorbed.items():
        gone = sorted(s for s in sources if s.startswith(PREFIX) and s not in classes)
        if gone:
            result[target] = gone
    return result


def main(paths: list[str]) -> int:
    if not paths:
        print(__doc__)
        return 2
    failed = False
    for path in paths:
        merged = merged_classes(path)
        for target, sources in sorted(merged.items()):
            failed = True
            print(f"::error file=app/proguard-rules.pro::{path}: R8 merged {', '.join(sources)} into {target}")
        if not merged:
            print(f"{path}: every Meta Spatial SDK class kept its own class.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
