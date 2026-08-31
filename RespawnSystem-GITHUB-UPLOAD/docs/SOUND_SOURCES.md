# Звуки интерфейса

Для интерфейса выбран набор **Kenney Interface Sounds**.

- Источник: https://opengameart.org/content/interface-sounds
- Прямая ссылка на архив: https://opengameart.org/sites/default/files/kenney_interfaceSounds.zip
- Лицензия: **CC0** (можно использовать и изменять без обязательной атрибуции).
- Формат исходников: OGG.

Используемая раскладка:

| Событие | Файл Kenney | Файл в моде |
|---|---|---|
| клик | `click_001.ogg` | `ui/click.ogg` |
| подтверждение | `confirmation_001.ogg` | `ui/confirm.ogg` |
| ошибка | `error_008.ogg` | `ui/error.ogg` |
| уведомление | `confirmation_002.ogg` | `ui/notification.ogg` |

Запусти `INSTALL_SOUNDS_WINDOWS.bat` перед сборкой. Если внешние OGG не установлены, код интерфейса всё равно использует встроенный `minecraft:ui.button.click` как безопасный fallback.
