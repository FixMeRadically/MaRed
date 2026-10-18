# MaRed / Genesis — startup-profile-18

Накопительный патч поверх 05–17, с backup/restore. Добавляет разовый профиль настоящей игровой JVM, чтобы выбрать следующую оптимизацию по данным.

## Текущее подтверждённое ускорение

Последний реальный restart пользователя: 15 936 ms, direct_cache/hit, launcher_gap=34 ms, shutdown=892 ms. До обхода Gradle сопоставимый restart занимал 24 361 ms: выигрыш 8 425 ms, около 35%. Оставшиеся участки: JVM→mod_loaded 8 677 ms, ресурсы/меню 4 172 ms, мир 2 159 ms. Setup MaRed — 19 ms.

18 не обещает дополнительного сокращения этих цифр: это диагностический этап. Он нужен, чтобы определить, доминируют ли загрузка/преобразования классов, архивы, моды, чтение ресурсов или GC, вместо очередной оптимизации нескольких миллисекунд MaRed setup.

## Что добавлено

- `tools/startup_profile.py . --arm` ставит одноразовый запрос. Без него обычный запуск не включает JFR.
- Запрос применяется только к следующему подходящему `direct_cache` запуску игровой JVM. Gradle JVM не профилируется; при Gradle fallback запрос остаётся ждать.
- Включается JDK Flight Recorder на 30 секунд, с sampling Java/native методов, class-load events, медленными FileRead от 5 ms, GC и паузами GC. После 30 секунд запись останавливается сама; при более раннем обычном выходе используется dump-on-exit.
- Заявка забирается атомарно. Ошибка подготовки оставляет/восстанавливает запрос и запускает обычную команду; ошибка создания процесса восстанавливает запрос. После успешного создания процесса заявка потреблена. Следующий restart снова без профиля.
- Пользовательские/унаследованные JFR flags и opaque @file launch inputs не заменяются: запрос сохраняется, игра запускается обычным путём. Инъекция происходит перед собственным Windows @argfile wrapping supervisor.
- Профиль и sidecar metadata получают уникальное имя по launch ID в `run/client/config/mared/dev/profiles`. До восьми профилей; очередной запрос при заполнении требует сначала архивировать старые. Recording maxsize=64 MiB, maxage=30 s; JFR chunking может немного превышать целевой размер. Анализатор ограничен 128 MiB, 2 млн событий на проход и 20 s обработки; wrapper timeout 30 s.
- Новый `StartupProfileReport` читает JFR потоково и сопоставляет uptime с `mod_loaded`, `resources_ready`, `world_ready` того же launch ID. Время JVM берётся из JVMInformation, без парсинга command line.
- `restart_last.py` добавляет `startup_profile_requested`: такой диагностический запуск не следует использовать как чистый benchmark обычного restart.

Режим «Применить на сервере» использует тот же supervisor restart после сборки/установки JAR. Кнопка не менялась. Для диагностического прогона удобнее команда restart: она исключает дополнительную сборку проекта из действий пользователя.

## Установка

Закрой Minecraft и supervisor. Распакуй комплект. Запусти `apply.cmd` либо из комплекта:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Из MaRed с JDK 21:

```powershell
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Clean и удаление кэша не нужны. Обновление основного кода законно инвалидирует прежний direct cache. После первого входа подожди 15 секунд, в Scripts выбери «Перезапустить Minecraft и вернуться в мир», если игра ещё не под supervisor. После возвращения снова подожди 15 секунд и проверь:

```powershell
py .\tools\restart_last.py .
```

Нужен `launch_cache_capture: cached` либо уже `direct_cache/hit`. Если кэш отклонён, сначала разбирается причина отказа, профилирование Gradle вместо игры не включается.

## Один диагностический restart

При работающей игре под supervisor, из второго PowerShell в MaRed:

```powershell
py .\tools\startup_profile.py . --arm
```

После сообщения Armed в чате Minecraft:

```text
/mared dev restart
```

Дождись возвращения в мир. Подожди ещё примерно 20 секунд (запись длится 30 секунд от запуска JVM), затем в PowerShell:

```powershell
py .\tools\startup_profile.py .
```

Если запись ещё не готова, подожди несколько секунд и повтори только команду отчёта. Новый restart для ожидания окончания записи не нужен. Пришли JSON, который выведет эта команда. Сырой JFR, sidecar JSON с путями, launch cache и @files присылать не требуется.

Запрос можно отменить до его применения:

```powershell
py .\tools\startup_profile.py . --cancel
```

Это отменяет ожидающую заявку. Уже начавшаяся запись сама закончится через 30 секунд. Она не включается заново при следующих перезапусках. `--arm` повторно при уже ожидающей правильной заявке безопасен; неизвестный request file не перезаписывается.

## Что означает отчёт

Три окна: `jvm_to_mod_loaded`, `mod_to_resources_ready`, `resources_to_world_ready`. Последнее включает переход к миру и загрузку мира; это не только прежний world_load_ms.

- `java_samples`, `native_samples` — число sampling events, не CPU milliseconds. Native samples не доказывают, что поток всё это время выполнялся на CPU.
- `top_sampled_methods` — верхний frame в sampled stack. `sample_groups` — первая узнаваемая группа в стеке: mixin, bytecode_asm, neoforge_loader, archive_io, minecraft, mared_genesis; остальное jdk_other. Это ориентиры для исследования, не точное распределение wall-clock времени между подсистемами.
- `class_load_events` — количество зарегистрированных загрузок. `slow_class_loads` показывает отдельные долгие загрузки; они могут быть вложенными, поэтому их durations нельзя складывать в общий startup time.
- `gc_pause_ms` — зарегистрированные GCPhasePause durations, отдельно от количества GC cycles.
- `slow_file_read_ms` и `slow_reads_by_jar_ms` учитывают только записанные FileRead от 5 ms. Это не всё дисковое IO: быстрые чтения и другие механизмы доступа сюда не попадают. Длительности параллельных чтений тоже нельзя считать эксклюзивным wall time.
- `recording_first_uptime_ms` / `recording_last_uptime_ms` показывают временное покрытие. До старта самого JFR часть ранней загрузки может не попасть в профиль; последний интервал может быть обрезан, если мир загружался больше 30 секунд.

Отчёт выводит методы/классы и имена JAR, без JVM arguments, environment, содержимого файлов, имён потоков и полных путей. Сырой локальный JFR может содержать private metadata JVM, включая launch arguments; нужен именно текстовый summary. Профилирование добавляет нагрузку и искажает замер скорости. После выбора/реализации следующей оптимизации сравниваются обычные запуски без заявки.

Профиль читается тем же JDK, которым запускалась игра, и helper из использованного supervisor capsule. Если проект/JDK перемещены либо capsule удалён новым activation, отчёт может потребовать повторного профиля. Для этого прогона сначала сформируй summary, затем закрывай/переактивируй supervisor.

## Проверки и ограничения

21 автономный suite: новый реальный JFR startup/streaming report, прежние capture/cache/input/IO/lease/lifecycle/activation/rollback/client-double/report/installer проверки. Новый fixture проверяет обычный запуск без flags, Gradle skip, сохранение пользовательского JFR, atomic claim/restore, реальные Java processes с Unicode/пробелами/запятой в пути, собственный @argfile wrapper, непустые execution/class-load events, Java→Python summary, arm/re-arm/cancel и отсутствие raw paths/arguments в summary. Installer: базы 05–17 и 16a (fixture 16.5), LF/CRLF, backup/apply/repeat/byte-exact restore.

Автономные проверки используют Linux/JDK 17. Настоящий Minecraft/NeoForge/Gradle runClient, JDK 21 на Windows и headful presentation здесь не запускались; поведение на пользовательской машине ещё нужно проверить. API doubles и ProcessHandle SKIP отмечены в логах. Профиль не является snapshot игры, не меняет моды/ресурсы/сохранения и не обещает нового ускорения до анализа реальных данных.

Документация JDK:
- https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html — StartFlightRecording;
- https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html — streaming reader.

Откат при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

Профили создаются в game config и не относятся к исходникам установщика. Откат кода не удаляет уже записанные пользовательские профили.
