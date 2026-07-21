#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

log() {
  printf '[postCreate] %s\n' "$1"
}

log "バックエンドの依存関係を確認します。"
if [ ! -f "${PROJECT_ROOT}/backend/pom.xml" ]; then
  log "backend/pom.xmlがないため、Maven依存関係の解決をスキップします。"
elif [ -f "${PROJECT_ROOT}/backend/mvnw" ]; then
  chmod +x "${PROJECT_ROOT}/backend/mvnw"
  (
    cd "${PROJECT_ROOT}/backend"
    ./mvnw dependency:go-offline
  )
elif command -v mvn >/dev/null 2>&1; then
  (
    cd "${PROJECT_ROOT}/backend"
    mvn dependency:go-offline
  )
else
  log "Maven Wrapperとmvnが見つかりません。"
  exit 1
fi

log "フロントエンドの依存関係を確認します。"
if [ ! -f "${PROJECT_ROOT}/frontend/package.json" ]; then
  log "frontend/package.jsonがないため、Node.js依存関係の解決をスキップします。"
elif [ -f "${PROJECT_ROOT}/frontend/pnpm-lock.yaml" ]; then
  (
    cd "${PROJECT_ROOT}/frontend"
    pnpm install --frozen-lockfile
  )
elif [ -f "${PROJECT_ROOT}/frontend/package-lock.json" ]; then
  (
    cd "${PROJECT_ROOT}/frontend"
    npm ci
  )
else
  log "対応するlockファイルがないため、依存関係のインストールを中止します。"
  exit 1
fi

log "開発環境の初期セットアップが完了しました。"
