#!/usr/bin/env python3
import os
import subprocess
import shutil
import sys
import pathlib
import textwrap
import venv as _venv


def ws_root() -> pathlib.Path:
    ws = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    return pathlib.Path(ws) if ws else pathlib.Path.cwd()


def main() -> int:
    ws = ws_root()
    venv_dir = ws / ".venv"
    lock_file = ws / "requirements.lock"

    print(f"[venv] Hermetic Python: {sys.version.split()[0]} @ {sys.executable}")

    venv_python = venv_dir / "bin" / "python"

    # Create venv (prefer symlinks to keep dylib resolution intact on macOS)
    def create_venv():
        print(f"[venv] Creating virtual environment at {venv_dir}")
        builder = _venv.EnvBuilder(with_pip=True, symlinks=True)
        builder.create(venv_dir)

    def healthy_python() -> bool:
        try:
            if not venv_python.exists():
                return False
            out = subprocess.run(
                [str(venv_python), "-V"], capture_output=True, text=True
            )
            return (
                out.returncode == 0
                and out.stdout.strip().startswith("Python ")
                or out.stderr.strip().startswith("Python ")
            )
        except Exception:
            return False

    if not venv_dir.exists():
        create_venv()
    else:
        print(f"[venv] Virtual environment already exists at {venv_dir}")
        if not healthy_python():
            print("[venv] Detected broken interpreter in .venv; rebuilding")
            shutil.rmtree(venv_dir, ignore_errors=True)
            create_venv()

    venv_pip = [str(venv_python), "-m", "pip"]

    print("[venv] Upgrading pip/wheel in the venv")
    subprocess.run(venv_pip + ["install", "--upgrade", "pip", "wheel"], check=True)

    if lock_file.exists():
        print(f"[venv] Installing dependencies from {lock_file.name}")
        subprocess.run(venv_pip + ["install", "-r", str(lock_file)], check=True)
    else:
        print(
            "[venv] No requirements.lock found at repo root; skipping dependency install"
        )

    msg = textwrap.dedent(
        """

        Virtual environment ready.

        Activate it with:

          source .venv/bin/activate

        IDE/LSP interpreter:

          .venv/bin/python

        To rebuild the venv after changing requirements.lock:

          bazel run //:venv
        """
    ).strip("\n")
    print(msg)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
