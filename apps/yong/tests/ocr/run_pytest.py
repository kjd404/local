import pathlib
import sys

import pytest


if __name__ == "__main__":
    target = pathlib.Path(__file__).with_name("test_paddle_service_bin.py")
    sys.exit(pytest.main(["-q", str(target)]))
