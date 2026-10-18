# MaRed / Genesis — lease-io-14

Накопительный патч с backup/restore поверх 05–13. Оптимизирует подтверждение выхода игровой JVM и обработку вывода во время загрузки. Включает direct cache 12 и поэтапную загрузку 13.

## Изменения

### Подтверждение реального выхода

В 10–13 независимый heartbeat отличает игровую JVM от живого launcher. Когда OS API скрывает PID/start metadata, сигнал EXITED ещё не означает конец процесса: он пишется из shutdown hook, пока другие hooks могут продолжать сохранять данные. Поэтому применялись защитные задержки. При обычном завершении дополнительно ожидалась передача кода выхода через wrapper.

В 14 игровая JVM создаёт в своём уникальном runtime-каталоге `game.lease` и удерживает FileLock. Канал и lock сохраняются сильными ссылками на всю жизнь успешной сессии. Shutdown hook не закрывает их. ОС освобождает lock после завершения JVM, включая Runtime.halt.

- Удерживаемая блокировка при скрытых OS metadata означает, что игровая JVM ещё не закончила работу. Ранний EXITED и задержка heartbeat не обходят это условие.
- Освободившаяся блокировка подтверждает выход только при совпадении собственного marker: protocol, launchId, отдельный lease token, PID/start. Отсутствие файла или повреждённый marker не считается доказательством выхода.
- Доступный OS API остаётся первым источником: живой процесс с той же идентичностью не объявляется завершённым из-за файла.
- Если lease не удалось создать, сохраняется прежний механизм heartbeat/OS с таймаутами. Если token уже был зарегистрирован, а lease стал непроверяемым, он не превращается в legacy EXITED proof.
- Неактивная запись очищается только после подтверждённого завершения принадлежащего supervisor дерева. Дополнительно блокируется второй запуск, если wrapper уже закончился, а зарегистрированная игровая JVM ещё не имеет подтверждения выхода.
- Для подтверждённого нормального завершения убрано дополнительное двухсекундное ожидание wrapper. Для неоднозначного/аварийного выхода ожидание передачи exit status сохранено.
- Кооперативный restart переходит дальше после реального выхода игры; запись EXITED сама по себе не сокращает работу shutdown hooks. Настроенный бюджет принудительного stop сохраняется.

FileLock — дополнительный сигнал жизненного цикла, а не способ сохранить готовую игру в памяти. На настоящем Windows и конкретной файловой системе его поведение здесь не проверялось. Неподдерживаемые/неоднозначные lock операции возвращают UNKNOWN и не используются как быстрый exit proof.

### Меньше накладных расходов на вывод

- Вывод читается блоками по 8192 символа вместо отдельного вызова Reader.read на каждый символ. Сохранены границы строк, CRLF, пустые строки, остаток при EOF, Unicode и лимит 16384 символа на часть длинной строки. Прежний Reader уже был buffered: изменение сокращает Java-вызовы/синхронизацию, а не обещает столько же меньше OS read syscalls.
- Файловый лог сбрасывает буфер примерно раз в 100 ms вместо каждого небольшого burst. При обычном close writer закрывается со сбросом оставшихся строк. При редком потоке опрос может добавить до 50 ms. Это диагностический лог, не транзакционный журнал мира.
- Форматированное время HH:mm:ss кэшируется на секунду вместо форматирования для каждой строки.
- In-memory tail по-прежнему обновляется сразу. Ограниченная очередь и сообщение о потерянном диагностическом выводе при переполнении сохраняются; заблокированная консоль не удерживает lifecycle/HTTP.

## Что измерено

В прежнем тестовом сценарии с настоящими fake game/lingering launcher JVM cooperative shutdown занимал примерно 2121 ms при бюджете 7000 ms. В первом прогоне 14 тот же сценарий показал 78 ms. Фактический результат последнего прогона записан в `verification/session-ownership-checks.log`.

Это измерение fixture в Linux с недоступными ProcessHandle metadata. Это не benchmark Minecraft/Windows и не изменение пользовательского результата 23,925 с. Ускорение касается конкретного ожидания завершения; initial handoff без старой управляемой сессии не обязан выиграть столько же. При доступном OS API прежний restart мог и так подтверждать выход быстро.

В отдельном тесте shutdown hook продолжает работать 3,2 секунды: даже после прежнего heartbeat timeout игра не считается завершённой, пока удерживает lease. После реального завершения released lease распознаётся без фиксированной паузы. Runtime.halt тоже проверяется.

IO fixture: 40028 символов обрабатываются 6 вызовами Reader.read с тем же результатом строк. Это проверка алгоритма, не оценка ускорения всей загрузки. Буферизация логов может уменьшить расходы при обильном выводе; размер реального выигрыша пока неизвестен.

Основная загрузка Minecraft/NeoForge, включая прежние 8,328 с JVM→mod_loaded, этим патчем не объявляется ускоренной втрое. Полного snapshot/ресурсного кэша здесь нет. Перед следующими изменениями нужен новый реальный замер после direct-cache и staged-startup.

## Установка

Закрой Minecraft и supervisor. Распакуй комплект, запусти `apply.cmd` для `%USERPROFILE%\Desktop\MappetPort\MaRed` либо:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Установщик проверяет все затронутые файлы до записи, сохраняет backup и не заменяет неизвестные локальные правки. Для отката при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

Из MaRed, JDK 21, по отдельности; при ошибке остановись:

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean --no-parallel
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Обновить нужно основной мод и вложенный supervisor-bootstrap. Старая запущенная JVM не обновляется от замены исходников.

После первого входа подожди несколько секунд для кэша. Выполни два управляемых restart в том же мире. Затем:

```powershell
py .\tools\restart_last.py .
```

Второй restart важен: исходная передача управления может иметь cycle_kind=initial и shutdown_ms=null. В новом отчёте есть shutdown_confirmation: file_lease, os, launcher, legacy_marker, unconfirmed либо unrecorded. Это использованный источник до reap; при принудительном stop без предварительного exit proof возможен unconfirmed. launch_mode и fast_launch_reason сохраняются.

Статус/identity API сохраняет protocol 4 и lifecycleVersion 10, добавляет processLeaseVersion=1 и gameLease=HELD/RELEASED/UNKNOWN/LEGACY/NONE. Токен lease не выводится через API. Сам файл game.lease и session.properties принадлежат только конкретному runtime launch и очищаются после reap. Не удаляй их вручную при работающей игре.

Обычное закрытие должно завершать supervisor и освобождать порт; restart — возвращать игру. Проверь повторный запуск без ручного taskkill. При проблеме нужны компактный timing report и supervisor.log/bootstrap.log; не присылай supervisor.properties, direct-launch cache или seed/@argfiles с приватными параметрами.

Дальность из 13 по-прежнему восстанавливается постепенно при supervised local entry. Явное fastWorld.enabled=false в config/mared/dev/startup.properties отключает этот профиль.

## Проверки

17 наборов: новые реальные hook/halt JVM с принудительно UNKNOWN OS probe; local lock/missing/corrupt marker; блоковое чтение/Unicode/CRLF/bounds/EOF; flush финальных строк; cooperative restart с tightened deadline; прежние активная/чужая сессия, late exit cancellation, rollback/trial, direct cache, staged startup и installer. API doubles в клиентских тестах явно обозначены. Накопительный installer проверяет базы 05–13 и LF/CRLF, backup/apply/repeat/byte-exact restore.

Полная Gradle/NeoForge-сборка, Minecraft, headful Windows и реальные performance timings здесь не выполнялись. Автономные проверки используют Linux/JDK 17; проект требует JDK 21. Successful parent handoff и descendants остаются SKIP при скрытых ProcessHandle metadata.
