#!/usr/bin/env python3
"""
Flashable ZIP Builder for HyperDeX Tablet Module
Compatible with KernelSU, Magisk, and APatch
"""

import os
import sys
import zipfile
import hashlib
import shutil

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULE_SRC = os.path.join(PROJECT_DIR, "module")
RELEASE_DIR = os.path.join(PROJECT_DIR, "release")
ZIP_NAME = "HyperDeX_v1.0.1.zip"
ZIP_OUTPUT = os.path.join(RELEASE_DIR, ZIP_NAME)
SDCARD_DOWNLOAD = "/sdcard/Download"

EXCLUDE_DIRS = {".git", "release", "__pycache__", ".idea", ".vscode"}
EXCLUDE_FILES = {".DS_Store"}


def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


def build_module():
    print(f"[*] Packaging HyperDeX module from: {MODULE_SRC}")
    os.makedirs(RELEASE_DIR, exist_ok=True)

    if os.path.exists(ZIP_OUTPUT):
        os.remove(ZIP_OUTPUT)

    with zipfile.ZipFile(ZIP_OUTPUT, "w", compression=zipfile.ZIP_DEFLATED) as zout:
        for root, dirs, files in os.walk(MODULE_SRC):
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            for f in files:
                if f in EXCLUDE_FILES or f.endswith((".pyc", ".bak")):
                    continue
                f_full = os.path.join(root, f)
                f_rel = os.path.relpath(f_full, MODULE_SRC)
                zout.write(f_full, f_rel)
                print(f"  + {f_rel}")

        # Also bundle README and LICENSE
        for doc in ["README.md", "LICENSE"]:
            doc_path = os.path.join(PROJECT_DIR, doc)
            if os.path.exists(doc_path):
                zout.write(doc_path, doc)
                print(f"  + {doc}")

    file_size_mb = os.path.getsize(ZIP_OUTPUT) / (1024 * 1024)
    checksum = sha256_file(ZIP_OUTPUT)
    print(f"\n[✓] Module packaged successfully!")
    print(f"    Path: {ZIP_OUTPUT}")
    print(f"    Size: {file_size_mb:.2f} MB")
    print(f"    SHA-256: {checksum}")

    # Copy to /sdcard/Download if accessible
    if os.path.isdir(SDCARD_DOWNLOAD):
        target_sdcard = os.path.join(SDCARD_DOWNLOAD, ZIP_NAME)
        try:
            shutil.copy2(ZIP_OUTPUT, target_sdcard)
            print(f"[✓] Copied to storage for instant flashing: {target_sdcard}")
        except Exception as e:
            print(f"[!] Note: Could not copy to /sdcard/Download: {e}")

    # Write checksum file
    with open(os.path.join(RELEASE_DIR, "SHA256SUMS.txt"), "w") as f:
        f.write(f"{checksum}  {ZIP_NAME}\n")


if __name__ == "__main__":
    build_module()
