# MaRed / Genesis — direct-cache-15

Накопительный патч поверх 05–14, с backup/restore. Основная цель — использовать прямой запуск игровой JVM вместо повторного Gradle там, где код и параметры запуска остались актуальными.

## Почему это приоритет

Последний реальный замер пользователя: 23 647 ms всего, launch_mode=gradle, fast_launch_reason=stale_inputs. На launcher_gap приходится 7 491 ms; JVM→mod_loaded — 8 848 ms; ресурсы/меню — 4 216 ms; мир — 2 206 ms. MaRed setup всего 30 ms.

Это делает Gradle самым крупным подтверждённым участком, который уже можно обходить существующим direct-launch путём. При этом точная причина stale_inputs из старого отчёта неизвестна. Исправления ниже устраняют воспроизведённые ложные сбросы и дают диагностику оставшихся причин. Выигрыш в Minecraft пока не измерен: 7,5 секунды — размер прежнего участка, а не обещанная экономия. Одна эта мера не превращает 24 секунды в 8.

## Что исправлено

- Native library search paths отделены от build inputs. Вместо рекурсивного обхода целого каталога проверяются непосредственно находящиеся в нём native-библиотеки (.dll/.so/.so.*/.dylib/.jnilib). Логи, config, saves и вложенные script_projects не становятся входами сборки только из-за java.library.path. Добавление, изменение и удаление native-библиотеки сбрасывают кэш.
- Native extraction после старта JVM не ломает предзапусковой baseline сборки: native-файлы фиксируются в итоговом подтверждённом кэше. Проверка неизменности исходников/выходов сборки между Gradle exec и подтверждением сохранена.
- Входы source sets берутся из main; фактический JavaExec classpath также проверяется. Test output не инвалидирует игру, если его нет в реальном runtime classpath. Добавлены Gradle config directory и исходники/скрипты buildSrc без его build/.gradle.
- Проектные JAR проверяются по содержимому. Пересоздание идентичных байтов с новой датой не сбрасывает кэш; изменение байтов с прежней датой обнаруживается. Для внешних dependency JAR сохранена прежняя ограниченная проверка size/mtime.
- Все нормализованные эквиваленты временного @argfile заменяются постоянной копией; относительные @file/javaagent/native paths разрешаются относительно рабочей директории игры. Удаление исходного временного файла не оставляет случайную вторую ссылку во входах.
- Вместо одного непрозрачного fingerprint сохраняются ограниченные хэши входных корней. При отказе отчёт показывает до восьми изменившихся корней. Внешние пути сокращаются до имени; аргументы, environment, токены и содержимое @files в диагностику не выводятся.
- Появился результат фонового подтверждения кэша в том же timing cycle. Теперь можно отличить отсутствующий seed, уже использованный seed и отказ INPUT_CHANGED_DURING_CAPTURE от успешной записи.

Ограничения проверки: 2 s, 30 000 уникальных entries, 96 MiB чтения содержимого, bounded input count. Symlinks в build inputs и native-библиотеках не допускаются. Неоднозначный/превышающий лимиты кэш возвращает обычный launcher. Кэш не обновляется простым принятием текущих изменившихся файлов: для новых исходников по-прежнему нужна сборка.

## Установка и проверка

Закрой Minecraft и supervisor. Распакуй комплект; `apply.cmd` применяет патч к `%USERPROFILE%\Desktop\MappetPort\MaRed`, либо из папки комплекта:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Установщик проверяет все затронутые пути перед записью и сохраняет backup. Неизвестные локальные правки блокируют применение. Затем из MaRed с JDK 21:

```powershell
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Обновляются основной мод и вложенный supervisor-bootstrap. `clean` для этого патча не обязателен. При ошибке сборки не запускай следующую команду.

Формат кэша обновлён. Старый кэш даст один fallback с `cache_upgrade_required`; рабочий Gradle-запуск должен записать новый после готовности игры. Войди в мир, подожди 10–15 секунд, затем выполни два управляемых restart **без сборок и изменений исходников между ними**. Если дальность ещё восстанавливается или открыт loading overlay, фоновое подтверждение может начаться позже.

После готовности мира и небольшой паузы:

```powershell
py .\tools\restart_last.py .
```

Ожидаемый признак обхода Gradle:

```json
{
  "launch_mode": "direct_cache",
  "fast_launch_reason": "hit",
  "fast_launch_changed_inputs": []
}
```

Сравни `total_ms` и `launcher_gap_ms` с 23 647 и 7 491 ms. Для оценки повторного restart учитывай `cycle_kind`: initial и restart имеют разные shutdown затраты.

При отказе теперь возможен, например:

```json
{
  "launch_mode": "gradle",
  "fast_launch_reason": "stale_inputs",
  "fast_launch_changed_inputs": ["build/classes/java/main"],
  "launch_cache_capture": "cached"
}
```

Здесь причиной текущего fallback стали changed inputs, а `cached` означает, что уже запущенная игра записала новый кэш для следующего restart. Это два разных момента. `launch_cache_capture` отсутствует, пока фоновая задача/доставка метрики не завершилась. Значения: cached — записан; consumed — маркер от уже использованного seed (обычно direct replay); no_seed — нет маркера захвата; input_changed_during_capture — сборочные входы изменились до подтверждения; прочие значения — ограниченный код отказа. При таких отказах не удаляй проверки: пришли компактный отчёт. Не присылай direct-launch.properties, supervisor.properties, seed или @argfiles с приватными параметрами.

Откат при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

## Проверки и пределы результата

18 автономных наборов: реальные файлы/native extraction, JAR bytes/mtime, source/native invalidation, миграция старого кэша, duplicate/relative @files с настоящим Java child replay, RestartMetrics→restart_last, предыдущие lifecycle/lease/activation/trial/rollback/IO/staged startup/visual/installer проверки. Installer проверяет базы 05–14, LF/CRLF, backup/apply/repeat/byte-exact restore. Детали — verification/*-checks.log. Клиентские API doubles явно обозначены.

Полная Gradle/NeoForge-сборка, lazy JavaExec hook в реальном runClient, Minecraft и headful Windows здесь не выполнялись. Проверки используют JDK 17, проект требует JDK 21. ProcessHandle-dependent parent handoff/descendants остаются SKIP там, где host скрывает metadata. Пользовательский stale_inputs мог иметь иную причину, чем исправленные случаи; новые changed-input и capture status поля нужны для её проверки.
