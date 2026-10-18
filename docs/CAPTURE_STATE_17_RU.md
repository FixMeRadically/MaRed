# MaRed / Genesis — capture-state-17

Накопительный патч поверх 05–16a с backup/restore. Исправляет Java String/Groovy GString mismatch в seed marker и ошибочную диагностику consumed после неудачного захвата.

## Что показали два новых замера

| Поле | initial | restart |
|---|---:|---:|
| total_ms | 23932 | 24361 |
| shutdown_ms | null | 900 |
| launcher_gap_ms | 7672 | 8134 |
| jvm_to_mod_loaded_ms | 8965 | 8745 |
| resources_and_menu_ms | 4168 | 4345 |
| world_load_ms | 2229 | 2243 |

В обоих launch_mode=gradle, fast_launch_reason=cache_upgrade_required, launch_cache_capture=consumed. Ускорение не включилось; новый кэш не заменил старый. Consumed при новом Gradle launch был неверным объяснением отсутствующего seed, а не доказательством успешного кэширования.

## Причина и исправление

В 16/16a marker создавался через def с интерполяцией, то есть GString. Список allJvmArgs был преобразован в обычные Java String, затем проверялся через List.contains(marker). Java collection equality не делает Groovy == coercion: GString.equals(String) возвращает false. Проверка могла отказать, хотя текст marker присутствовал. Catch возвращал marker даже без созданного seed. LaunchCacheClient видел отсутствие файла и объявлял consumed, как при настоящем replay, не различая эти ситуации.

В 17 marker factory возвращает Java String явно, локальная переменная также объявлена String. Добавлен лёгкий Gradle task genesisCaptureChecks, зависимость стандартного check/build. Он вызывает тот же factory и проверяет тип/contains без старта Minecraft, записи seed или запуска дополнительной JVM. Этот Groovy check здесь не исполнялся: он выполнится при пользовательском build.

После попытки захвата Gradle передаёт игре отдельное bounded состояние ready / failed_<stage>. В рекурсивный snapshot оно не включается; повторный direct replay не наследует ready от уже использованного seed. Одна попытка на JavaExec; reentrant allJvmArgs возвращает только seed marker. Ошибка не повторяется на каждом чтении provider и не маскируется последующим статусом.

LaunchCacheClient теперь различает:
- cached — новый кэш записан;
- consumed — повторный запуск сохранённой команды с уже использованным marker (или legacy команда без capture state);
- capture_seed_missing — Gradle объявил ready, но нового seed нет;
- capture_failed_java_entry / jvm_options / application / assemble / build_inputs / native_inputs / baseline / write_seed — конкретный этап отказа захвата;
- capture_failed_unavailable — неизвестное состояние, без переноса исходного значения в отчёт.

При failed_* кэш не принимается даже при существующем seed; предыдущий кэш не перезаписывается. Ошибки ready/failed не превращаются в consumed. Существующие проверки происхождения файла, JDK, fingerprint, baseline, private @files и custom launch command сохранены. Полные команды, credentials и environment в диагностике не публикуются. Формат fingerprint 15 не меняется.

Справка о GString.equals: https://github.com/groovy/groovy-core/blob/master/src/main/groovy/lang/GString.java . Это анализ исходника и исправленного кода; прежний отчёт не сохранял конкретную строку Gradle exception на машине пользователя.

## Применение и проверка

Закрой Minecraft и supervisor. Распакуй комплект. Запусти apply.cmd либо из папки комплекта:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Затем из MaRed с JDK 21:

```powershell
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Build должен включать Task :genesisCaptureChecks. Clean/удаление cache не нужны. При ошибке сборки остановись. Зайди в мир/на сервер, подожди 15 s. В Scripts выбери «Перезапустить Minecraft и вернуться в мир», чтобы включить supervisor. После возвращения подожди 15 s. В другом PowerShell из MaRed:

```powershell
py .\tools\restart_last.py .
```

Если cached — кэш готов для следующего restart. Если уже direct_cache/hit и consumed — он использован. В этих случаях можно один раз выполнить в чате /mared dev restart и повторить отчёт после готовности мира. Если capture_failed_* / capture_seed_missing / другой отказ — достаточно отчёта; не делай повторные restart по кругу. Для запуска самого измерения не нужно собирать chatimagefix внутри игры.

Реальная экономия секунд ещё не измерена. Пока сохраняется gradle/cache_upgrade_required, приписывать изменениям ускорение нельзя.

Откат при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

## Проверки

В 17 заново запускаются capture-state, javaexec-capture, launch-cache и cache-inputs JDK suites, report и installer. Новые реальные child JVM проверяют ready без seed, failed_jvm_options/write_seed, unknown status, отсутствие ложного consumed и сохранение предыдущего cache; direct replay/полное успешное подтверждение покрыты предыдущими capture suites. Проверяется цепочка RestartMetrics→restart_last для нового отказа. Installer: базы 05–16a, LF/CRLF, apply/repeat/byte-exact restore, unknown edit и I/O rollback. В installer log fixture 16.5 означает базу 16a.

Остальные логи прежних 19 наборов сохранены как исторические проверки 16/16a; полный broad suite в этом патче не объявляется повторно выполненным. Набор run-checks.py теперь включает 20 suites для повторного полного прогона при необходимости.

Gradle task genesisCaptureChecks, полный lazy JavaExec hook в настоящем Gradle/NeoGradle, Minecraft и headful Windows здесь недоступны и не проверены запуском. Java-тесты на JDK 17; проект требует 21. Граница предыдущего тестирования была недостаточной: Java fixtures не выявили Groovy type mismatch. Новый build-time check проверяет этот конкретный типовой контракт в реальном Gradle при сборке пользователя.
