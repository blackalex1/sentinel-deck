<div align="center">

# 🎮 Sentinel Deck

### Интерактивная настольная консоль и дашборд рабочего места для Android

[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84.svg?style=flat-square&logo=android)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Ecosystem: Sentinel](https://img.shields.io/badge/Ecosystem-Sentinel-8a2be2.svg?style=flat-square)](https://github.com/blackalex1)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)

<p align="center">
  Клиентское приложение экосистемы <b>Sentinel</b> для планшетов и смартфонов, превращающее мобильное устройство в постоянную настольную док-станцию (Command Deck) для мониторинга ПК, умного дома и мультимедиа.
</p>

</div>

---

## 🌟 Возможности

* **📊 Мониторинг ПК (System Telemetry):**
  * Графики загрузки CPU, GPU, температур ядер и видеокарты (интеграция с LibreHardwareMonitor / MSI Afterburner).
  * Оперативная память, дисковое пространство накопителей.
* **🎵 Единый медиацентр (Unified Media Widget):**
  * Поддержка множества источников с переключением в 1 клик: **Sentinel Music**, **Яндекс.Станции (Glagol API)**, **PC Media (Windows)**.
  * Плавный прогресс-бар (`SmoothProgressBar`) с локальной интерполяцией без рывков.
  * Полноэкранный караоке-режим с синхронизированной построчной подсветкой текста песен (LRCLIB, Sentinel Server, Яндекс).
* **🌐 Сеть и роутер (Keenetic & Mihomo):**
  * Мониторинг скорости входящего и исходящего трафика роутера Keenetic.
  * Список активных клиентов домашней сети и Mesh Wi-Fi.
  * Управление режимами проксирования Mihomo (Clash Meta), селектор узлов и замер задержки (ping).
* **🚀 Панель макросов (App & Action Launcher):**
  * Быстрый запуск программ, горячие клавиши, блокировка ПК, перевод в сон, отключение микрофона.
* **📹 Виртуальная камера (Webcam Companion):**
  * Трансляция видеопотока с камеры планшета на ПК через ADB / RTSP с низкой задержкой для использования в качестве веб-камеры.
* **🎨 Кастомизация и стек:**
  * Полноценный Material 3 / Modern Glassmorphism дизайн.
  * Цветовая палитра синхронизируется с сервером **Sentinel Hub**.
  * Поддержка мультиязычности (RU / EN).

---

## 🏛 Архитектура связи

**Sentinel Deck** подключается к **Sentinel Hub** по локальной сети через WebSocket:
* Автообнаружение сервера в локальной сети по mDNS (Zeroconf).
* Сквозное шифрование сессий (AES-256 / токены сопряжения).
* Поддержка Standalone-режима: прямое управление колонками по Wi-Fi даже при выключенном ПК.

---

## 🛠 Сборка и запуск

### Требования:
* **Android Studio Ladybug (2024.2+)** или новее.
* **JDK 17** или **JDK 21**.
* Android SDK 35 (Android 15), минимальная версия Android 8.0 (API 26).

```bash
# Клонирование
git clone https://github.com/blackalex1/sentinel-deck.git
cd sentinel-deck

# Сборка Debug APK
./gradlew assembleDebug

# Запуск тестов
./gradlew testDebugUnitTest
```

Готовый APK появится в: `app/build/outputs/apk/debug/app-debug.apk`.

---

<div align="center">
Часть экосистемы <b>Sentinel</b> • Разработано с ❤️
</div>
