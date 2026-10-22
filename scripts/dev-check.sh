#!/usr/bin/env bash
# Одна команда для проверки этапов 0-1: тесты, сборка, запуск сервера в Docker, проверка лога.
# Использование: ./scripts/dev-check.sh
set -uo pipefail
cd "$(dirname "$0")/.."

fail() { echo "FAIL: $*" >&2; exit 1; }

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "Docker не найден или не запущен."
  if [ "$(uname)" = "Darwin" ]; then
    read -r -p "Установить/запустить Docker Desktop сейчас? [y/N] " ans
    [ "$ans" = "y" ] || fail "Без Docker продолжить нельзя."
    bash ./scripts/install-docker-mac.sh || fail "Не удалось установить/запустить Docker"
  else
    fail "Установите Docker Engine и docker compose."
  fi
fi

echo "==> Тесты и сборка"
./gradlew test deploy || fail "gradle test/deploy"

echo "==> Карта арены"
docker compose down >/dev/null 2>&1
bash ./scripts/reset-arena.sh || echo "ПРЕДУПРЕЖДЕНИЕ: карта арены не скопирована (нужна папка maps/arena), арена будет тестовой платформой"

echo "==> Запуск сервера (первый старт скачивает Paper, это несколько минут)"
docker compose up -d || fail "docker compose up"

for i in $(seq 1 120); do
  if docker compose logs paper 2>&1 | grep -q 'Done ('; then break; fi
  sleep 5
done

LOG=$(docker compose logs paper 2>&1)
check() { if echo "$LOG" | grep -q "$1"; then echo "OK   $2"; else echo "FAIL $2"; RES=1; fi; }
RES=0
check 'Done (' "сервер запустился"
check 'Duels enabled' "плагин включён"
check "Создан пустой мир 'duels_hub'" "хаб создан"
check "Создан пустой мир 'duels_lobby'" "лобби создано"
check "\(мир\|карта\) 'duels_arena'" "арена доступна (карта или тестовая платформа)"
check 'Загружено предметов: брони 56, оружия 21; наборов: 6' "предметы и наборы загружены"
if echo "$LOG" | grep -q 'ERROR\]'; then
  echo "FAIL в логе есть ERROR:"; echo "$LOG" | grep 'ERROR\]' | cut -c1-300 | head -10; RES=1
fi
if echo "$LOG" | grep -Ei 'Could not load|Error occurred while enabling|Exception' | grep -qi duels; then
  echo "FAIL в логе есть ошибки плагина:"; echo "$LOG" | grep -Ei 'duels' | grep -Ei 'error|exception|could not' | head -10; RES=1
fi

echo
[ $RES -eq 0 ] && echo "ВСЁ ХОРОШО. Заходите клиентом на localhost и проходите ручные проверки из README.md" || { echo "Есть проблемы. Последние 60 строк лога:"; echo "$LOG" | tail -60; }
exit $RES
