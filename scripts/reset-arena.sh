#!/usr/bin/env bash
# Копирует карту арены из maps/arena в мир сервера docker/data/duels_arena.
# Запускать при первом старте и каждый раз, когда нужно вернуть карту в исходное состояние.
# Сервер при этом должен быть остановлен: docker compose down
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f maps/arena/level.dat ]; then
  echo "Нет maps/arena/level.dat. Положите папку мира арены в maps/arena." >&2
  exit 1
fi
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx duels-paper; then
  echo "Сервер запущен. Сначала: docker compose down" >&2
  exit 1
fi

mkdir -p docker/data
# Paper 26 при первой загрузке переносит мир из корня в world/dimensions/minecraft/duels_arena.
# Старую перенесённую копию убираем, иначе сервер загрузит её, а не свежую карту.
rm -rf docker/data/duels_arena docker/data/world/dimensions/minecraft/duels_arena
cp -R maps/arena docker/data/duels_arena
echo "Карта арены скопирована в docker/data/duels_arena"
