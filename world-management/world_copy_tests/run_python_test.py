#!/usr/bin/env python3
"""End-to-end test of the Python reference copier (copy_world.py).

Entrypoint for the Python half of the world-copy tests:
  generate inputs -> copy each fixture with copy_world.py -> validate.
The Java half lives in run_java_test.py.
"""
import os
import sys

import _runner


def copy_world(name: str) -> None:
    src = os.path.join(_runner.INPUTS, name)
    dst = os.path.join(_runner.OUTPUTS, name)
    _runner.run([sys.executable, _runner.COPY_WORLD, src, dst])


def main() -> None:
    _runner.generate_inputs()
    _runner.clean_outputs()
    for name in _runner.fixture_names():
        copy_world(name)
    _runner.validate()
    print("\nPython world-copy end-to-end test passed.")


if __name__ == "__main__":
    main()
