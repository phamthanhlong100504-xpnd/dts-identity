#!/bin/bash
# Setup .agents/ submodule for dts-identity
# Run this from the project root directory
set -e

echo "=== Adding .agents submodule ==="
git submodule add -b main https://github.com/phamthanhlong100504-xpnd/doc-manual.git .agents

echo "=== Initializing submodule ==="
git submodule update --init --recursive

echo "=== Done! .agents/ is ready ==="
echo "Run 'git submodule update --remote .agents' to update later."
