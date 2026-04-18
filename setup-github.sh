#!/bin/bash

# ==============================================
# СКРИПТ ПЕРВОЙ ПУБЛИКАЦИИ MetaVision НА GITHUB
# ==============================================

echo "🚀 НАСТРОЙКА GITHUB ДЛЯ MetaVision"
echo "=================================="

# Твои данные
GITHUB_USERNAME="AndreyHhh"
REPO_NAME="MetaVision"

FULL_URL="https://github.com/$GITHUB_USERNAME/$REPO_NAME"

echo ""
echo "📁 Текущая папка: $(pwd)"
echo "👤 Пользователь: $GITHUB_USERNAME"
echo "📦 Репозиторий: $REPO_NAME"
echo ""

# 1. Инициализация Git
if [ ! -d ".git" ]; then
    echo "🔧 Инициализация Git..."
    git init
    echo "✅ Git инициализирован"
else
    echo "✅ Git уже инициализирован"
fi

# 2. Добавление всех файлов
echo ""
echo "📎 Добавляю файлы..."
git add .
echo "✅ Файлы добавлены"

# 3. Первый коммит
echo ""
echo "💾 Создаю первый коммит..."
git commit -m "Первая версия MetaVision 1.0"
echo "✅ Коммит создан"

# 4. Переименовываем ветку в main
echo ""
echo "🌿 Настраиваю ветку main..."
git branch -M main

# 5. Привязка к GitHub
echo ""
echo "🔗 Привязываю к GitHub..."
git remote add origin "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git" 2>/dev/null || git remote set-url origin "https://github.com/$GITHUB_USERNAME/$REPO_NAME.git"
echo "✅ Привязка к origin"

# 6. Отправка на GitHub
echo ""
echo "⬆️  Отправляю на GitHub..."
echo ""
echo "⚠️  Сейчас GitHub спросит логин и пароль."
echo "    Логин: $GITHUB_USERNAME"
echo "    Пароль: нужен Personal Access Token (создай в Settings → Developer settings → Tokens)"
echo ""

git push -u origin main

# 7. Результат
if [ $? -eq 0 ]; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║                     ✅ ВСЁ ГОТОВО!                         ║"
    echo "╠════════════════════════════════════════════════════════════╣"
    echo "║  Твой код теперь здесь:                                    ║"
    echo "║  $FULL_URL"
    echo "╚════════════════════════════════════════════════════════════╝"
else
    echo ""
    echo "❌ Ошибка при отправке."
    echo ""
    echo "🔧 Проверь:"
    echo "   1. Репозиторий $REPO_NAME создан на GitHub?"
    echo "   2. Логин и токен введены правильно?"
fi
