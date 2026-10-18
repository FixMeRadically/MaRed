# MaRed / Genesis — capture-fix-16

Накопительный патч поверх 05–15, backup/restore. Исправляет захват команды игровой JVM, который мог блокировать direct-launch cache с общим unconfirmed.

## Новый реальный замер

Пользователь получил restart 24 151 ms, launch_mode=gradle, fast_launch_reason=cache_upgrade_required, launch_cache_capture=unconfirmed. Shutdown=810 ms с OS proof; launcher_gap=8 645 ms; JVM→mod_loaded=8 301 ms; ресурсы/меню=4 217 ms; мир=2 172 ms. MaRed setup=21 ms, background launch_cache=3 ms.

Это не подтверждает ускорение: direct cache не использован. Старый кэш требует нового формата, но фоновое подтверждение нового seed отклонено. Повторные restart без исправления подтверждения не обязаны помочь. Initial и restart также нельзя сравнивать по total без учёта shutdown.

## Исправление

Предыдущий Gradle hook читал launchTask.commandLine как готовую полную JVM-команду. Проверенный исходник Gradle DefaultJavaExecSpec возвращает там ProcessArgumentsSpec command line отдельно от JVM options/main/classpath. Поэтому JVM marker мог отсутствовать в сохранённой команде, хотя сама игра его получила. Старые автономные тесты создавали полную команду сразу и не выявили ошибку Gradle-side захвата.

В 16 захват собирает команду из public JavaExec components: executable/toolchain, allJvmArgs, classpath, mainClass, args и application argumentProviders. Recursion guard возвращает тот же seed marker при чтении allJvmArgs; наличие marker проверяется до сохранения. Новый JDK-only JavaExecCommand формирует classic classpath/main либо single executable JAR entry. Inferred mainModule entry пока не захватывается: Gradle остаётся launcher. No OS argv / sun.java.command parsing, no additional JVM before startup.

Общий UNCONFIRMED разделён на коды: LOCATION, SEED_FILE, CAPTURE_ID, SCHEMA, WORK_DIR, COMMAND_COUNT, MARKER_MISSING, MARKER_PATH, JAVA_HOME с префиксом unconfirmed_. Они передаются в launch_cache_capture, без команд/токенов/аргументов. Эквивалентные нормализованные пути marker сравниваются как Path вместо сырой строки. Проверки происхождения seed, UUID, schema, working directory и JDK сохранены; отказ не превращается в принятие непроверенного кэша.

Формат fingerprint 15 сохранён: нет дополнительного обязательного обновления рабочего кэша 15. На пользовательской машине старый кэш ещё не был заменён, поэтому первый запуск может снова показать cache_upgrade_required. Решающий результат этого запуска — launch_cache_capture=cached. На следующем restart ожидаются launch_mode=direct_cache и fast_launch_reason=hit.

Исходники для проверки поведения API:
- https://chromium.googlesource.com/external/github.com/gradle/gradle/+/c2dbf5ab9f51c3c5b128e1c9d42d3f5fb6159b91/subprojects/core/src/main/java/org/gradle/process/internal/DefaultJavaExecSpec.java
- https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/JavaExec.html

Это проверенные исходники/API, а не локальный запуск конкретного Gradle 8.14.5 + NeoGradle 7.1.38. Какое именно условие старого UNCONFIRMED сработало на машине пользователя, прежний отчёт не сохраняет. Исправлен выявленный дефект захвата, остальные причины теперь различимы.

## Установка

Закрой Minecraft и supervisor. Распакуй архив; запусти apply.cmd либо из комплекта:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Затем из MaRed, JDK 21:

```powershell
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Сборка нужна один раз перед запуском игры; clean не обязателен. При ошибке сборки остановись. После готовности мира подожди 10–15 секунд (фоновая задача ждёт завершения loading overlay и восстановления дальности). В другом PowerShell из MaRed:

```powershell
py .\tools\restart_last.py .
```

Сначала проверь launch_cache_capture. Если cached — сделай **один** управляемый restart без сборок/правок между ними, после готовности мира снова выведи отчёт. Если появился unconfirmed_marker_missing / unconfirmed_java_home / другой конкретный отказ — достаточно этого отчёта, многократные restart не нужны. Сам seed/direct-launch.properties/@files присылать не нужно: они могут содержать приватные launch параметры.

При direct_cache/hit сравни launcher_gap_ms и total_ms с предыдущими измерениями. Реальный выигрыш Minecraft ещё не измерен; обход участка 8,6 s не означает гарантированную экономию ровно 8,6 s.

Установщик проверяет файлы до записи, сохраняет backup и блокирует неизвестные локальные изменения. Откат при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

## Проверки

19 наборов. Новый сценарий использует production JavaExecCommand, настоящую дочернюю JVM с system properties/assertions/Unicode/пустыми application args, фоновое подтверждение seed и второй реальный direct replay. Старый неполный command shape воспроизводится как UNCONFIRMED_MARKER_MISSING; отдельно проверены capture ID/schema/cwd/count/JDK/marker path отказы и нормализованный alias marker. Предыдущие lifecycle/lease/rollback/activation/cache/input/report/client-double проверки сохранены. Installer: базы 05–15, LF/CRLF, backup/apply/repeat/byte-exact restore. Подробности verification/*-checks.log.

Полная Gradle/NeoForge-сборка, lifecycle lazy argument providers в настоящем Gradle, Minecraft и headful Windows здесь не выполнялись. Автономные проверки на JDK 17; проект требует 21. Клиентские API doubles отмечены в логах. ProcessHandle-dependent parent handoff/descendants могут быть SKIP при скрытых metadata. Новые тесты проверяют assembler/confirmation, но не заменяют Gradle integration test.
