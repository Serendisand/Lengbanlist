#!/usr/bin/env bash
# 一次性启用仓库自带的 Git 钩子（子模块版本自动同步）。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
chmod +x .githooks/pre-commit
git config core.hooksPath .githooks
echo "已启用子模块版本自动同步钩子：.githooks/pre-commit"
