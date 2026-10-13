#!/usr/bin/env python3
"""
Convenience entry point for Cherish OTA release & upload.
Forwards directly to scripts/updater/upload.py
"""
import os
import sys
import subprocess

if __name__ == "__main__":
    script_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "scripts", "updater", "upload.py")
    result = subprocess.run([sys.executable, script_path] + sys.argv[1:])
    sys.exit(result.returncode)
