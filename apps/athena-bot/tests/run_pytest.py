import sys

import pytest


if __name__ == "__main__":
    # Discover and run tests in this directory
    sys.exit(pytest.main(["-q", __file__.rsplit("/", 1)[0]]))
