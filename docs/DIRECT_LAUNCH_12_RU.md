# MaRed / Genesis — direct-launch-12

Накопительный патч поверх 05–11 с backup/restore. Сохраняет lifecycle supervisor, профили мира, импорт/редактор и пиксельный переход предыдущих этапов.

## Основание

Пользовательский замер этапа 11, 15 октября 2026:

| Поле | Значение |
|---|---:|
| cycle_kind | initial |
| launch_mode | gradle |
| presentation_total_ms | 23925 |
| total_ms | 23796 |
| prepare_ms_parallel | 435 |
| spawn_ms | 36 |
| launcher_gap_ms | 7987 |
| jvm_to_mod_loaded_ms | 8328 |
| resources_and_menu_ms | 4257 |
| world_load_ms | 2227 |
| shutdown_ms | null |

Это измеренный запуск через Gradle; точная причина отказа старого direct-cache из отчёта не известна. Почти 8 секунд между launcher и игровой JVM — первый участок для устранения. Даже полное устранение этого интервала при неизменных остальных стадиях оставило бы около 16 секунд. Цель 8 секунд и трёхкратное ускорение этим патчем не подтверждены.

## Изменения

1. Захват команды переносится с `doFirst` на ленивый JVM argument provider `runClient`. `doFirst` только разрешает захват; снимок делается при вычислении аргументов. Reentrancy guard позволяет запросить полный JavaExec commandLine и не зациклиться на собственном provider. Seed содержит точные элементы argv, cwd и env overrides, без разбора строк `sun.java.command`/ProcessHandle.commandLine.
2. Отдельная Java-программа перед запуском игры больше не запускается. Gradle сохраняет небольшой private seed и metadata baseline файлов. Metadata helper вызывается из JDK-only bootstrap через закрываемый URLClassLoader; хеширование содержимого выполняется в daemon-потоке уже работающей игры после mod_loaded.
3. Только клиент с соответствующим JVM marker, cwd и Java home подтверждает seed. До и после фонового хеширования проверяется baseline: обычное изменение исходников/выходов между захватом и подтверждением отклоняет запись. Seed потребляется. Проверка содержимого файлов при использовании кэша сохраняется; изменённые исходники требуют запуска через Gradle. Metadata baseline не является защитой от намеренного восстановления size/mtime локальным процессом.
4. При подходящем кэше supervisor запускает JVM напрямую. Команда пользователя и fastLaunch.enabled=false сохраняют прежнее поведение. Кэш истекает через 24 часа. Если файлы изменились/кэш отсутствует/команда не поддержана, используется настроенный launcher.
5. Временные @argfiles копируются в уникальное поколение: новая попытка захвата не перезаписывает файлы ранее подготовленного запуска. Неудачная запись сохраняет прежний кэш и удаляет свои новые копии; старые копии старше 48 часов очищаются ограниченным проходом.
6. Повторяющиеся input paths и вложенные файлы хешируются один раз. Сохраняются лимиты времени, размера и числа файлов. Это оптимизация проверки, а не отказ от неё.
7. Отчёт `restart_last.py` включает `fast_launch_reason`. Supervisor пишет тот же enum в timing cycle. Причины не включают argv, environment или credentials.
8. Обычные dev runs используют console `info`; отладочные REGISTRIES-логи отключены. Для их возврата используй `-PmaredDebugLogs=true` при запуске через Gradle. При таком изменении параметров новый запуск создаст соответствующий кэш.

Прямой запуск уже существовал в 09–11. Этап 12 меняет получение/подтверждение кэша и его диагностику; он не является доказательством того, что любая причина прежнего fallback устранена на Windows.

## Установка и проверка

Закрой Minecraft и supervisor. Распакуй комплект и запусти `apply.cmd` для стандартного пути `%USERPROFILE%\Desktop\MappetPort\MaRed`, либо:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Установщик сначала проверяет весь payload, сохраняет backup, отказывается заменять неизвестные локальные правки. Для отката при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

Используй JDK 21 для Gradle JVM и toolchain. Metadata helper собран для Java 21; при старой Gradle JVM захват пропускается. Из MaRed запускай команды отдельно, прекращая при ошибке:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean --no-parallel
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

После входа в мир подожди несколько секунд. В консоли ожидаются:

```text
Genesis: effective JVM launch seed saved; validation will run in the game background.
[Genesis fast-launch] Runtime-confirmed direct launch cached in background
```

Примени изменение импортированного мода. После возврата в мир:

```powershell
py .\tools\restart_last.py .
```

Ожидаемый признак использования нового пути:

```text
launch_mode: direct_cache
fast_launch_reason: hit
```

Это критерий использования кэша, а не обещанное время запуска. Сравни presentation_total_ms и launcher_gap_ms на том же мире. При оставшемся gradle пришли весь компактный отчёт и две строки о захвате/отказе из консоли. Для последующих стадий остаются измерения jvm_to_mod_loaded_ms, resources_and_menu_ms и world_load_ms.

Возможные fast_launch_reason: no_cache, stale_inputs, expired, project_changed, disabled, custom_command, unconfirmed, fingerprint_time_limit, fingerprint_file_limit, fingerprint_byte_limit, linked_input, debug_launch, long_opaque_command, invalid_or_unsupported. Новая фоновая попытка дополнительно может сообщить INPUT_CHANGED_DURING_CAPTURE. При custom_command итоговый launch_mode=direct_config не обязательно означает медленный запуск: это сохранённая пользовательская команда.

Сборка импортированного проекта отдельно по-прежнему нужна: direct_cache обходит запуск корневого Gradle, а не сборку изменённого JAR. При правках исходников самого MaRed выполняется Gradle fallback, чтобы не запустить старые классы.

Не присылай direct-launch.properties, launch-seed-*.properties или direct-launch-arg-*.txt: они могут содержать приватные launch arguments и credentials.

## Пределы проверки

14 наборов JDK-only/process/API-adapter проверок: реальные дочерние JVM, файлы и HTTP, точность Unicode/кавычек/пустых аргументов, background seed confirmation и replay после удаления seed, изменение input между захватом и подтверждением, отказ чужого seed, предыдущие lifecycle/overlay/trial/installer проверки. Minecraft/NeoForge API в client adapter tests явно подменены. Gson fixture также является double.

Реальный Gradle JavaExec provider, NeoGradle 7.1.38, полный NeoForge build и Windows здесь не запускались. Поэтому ленивый hook остаётся интеграционной проверкой на машине пользователя. Successful handoff и live descendants — SKIP при скрытых metadata ProcessHandle. JDK-only тесты выполнялись на Linux/JDK 17; реальная сборка требует JDK 21.

Кэширование загруженных текстур/объектов/состояния мира и снимок процесса в этот патч не включены: безопасное восстановление этих данных требует отдельной разработки и измерений. Dynamic AppCDS остаётся прежней отдельной opt-in возможностью с ограничениями, автоматически не включается.
