import sys
import subprocess
import os

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    main_builder = os.path.join(script_dir, "build_vayuclient_hud.py")
    args = [sys.executable, main_builder] + sys.argv[1:]
    sys.exit(subprocess.call(args))
