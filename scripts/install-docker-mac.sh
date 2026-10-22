#!/usr/bin/env bash
# Установка Docker Desktop на macOS (Apple Silicon или Intel) и ожидание запуска.
# Нужен пароль администратора Mac (спросит sudo). Запускайте сами: bash scripts/install-docker-mac.sh
set -uo pipefail

docker_ready() { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; }

if docker_ready; then
  echo "Docker уже работает."
  exit 0
fi

install_with_brew() {
  command -v brew >/dev/null 2>&1 || return 1
  echo "==> Homebrew: brew install --cask docker-desktop"
  # В 2025 году cask переименовали: docker -> docker-desktop. Пробуем новое имя, потом старое.
  brew install --cask docker-desktop || brew install --cask docker
}

install_with_dmg() {
  case "$(uname -m)" in
    arm64) URL="https://desktop.docker.com/mac/main/arm64/Docker.dmg" ;;
    *)     URL="https://desktop.docker.com/mac/main/amd64/Docker.dmg" ;;
  esac
  TMP="$(mktemp -d)"
  echo "==> Скачивание: $URL"
  curl -fL --progress-bar -o "$TMP/Docker.dmg" "$URL" || { echo "Не удалось скачать Docker.dmg" >&2; return 1; }
  echo "==> Монтирование образа"
  hdiutil attach "$TMP/Docker.dmg" -nobrowse -quiet || { echo "Не удалось смонтировать Docker.dmg" >&2; return 1; }
  echo "==> Установка (потребуется пароль администратора)"
  sudo /Volumes/Docker/Docker.app/Contents/MacOS/install --accept-license --user="$USER"
  RC=$?
  hdiutil detach /Volumes/Docker -quiet || true
  rm -rf "$TMP"
  return $RC
}

if [ ! -d /Applications/Docker.app ]; then
  if ! install_with_brew; then
    echo "Через Homebrew не вышло, ставлю из dmg."
    install_with_dmg || { echo "Установка не удалась. Скачайте Docker Desktop вручную: https://www.docker.com/products/docker-desktop/" >&2; exit 1; }
  fi
fi

if [ ! -d /Applications/Docker.app ]; then
  echo "Docker.app не найден в /Applications после установки." >&2
  exit 1
fi

echo "==> Запуск Docker Desktop"
open -a Docker
echo "Если появится окно с лицензией или запросом прав (пароль для helper-компонентов), подтвердите его вручную."

for i in $(seq 1 90); do
  if docker_ready; then
    echo "Docker запущен."
    exit 0
  fi
  sleep 4
done
echo "Docker не ответил за 6 минут. Откройте Docker Desktop вручную, дождитесь зелёного статуса и повторите." >&2
exit 1
