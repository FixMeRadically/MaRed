# MaRed / Genesis — capture-fix-16a

Исправление ошибки Groovy compilation в патче 16: переменная main для mainClass и одноимённая переменная внутри allprojects.each конфликтовали. Переименованы в launchMainClass и sourceMainSet; использования обновлены. Java-логика supervisor/cache из 16 не меняется.

Ошибка компиляции build.gradle возникает до полной настройки проекта. Сообщение NeoGradle о toolchain в таком прогоне может быть следствием незавершённой конфигурации. В проекте уже задан java.toolchain.languageVersion=21; добавлять вторую настройку сейчас не требуется. Если сообщение останется после исправления Groovy, его нужно диагностировать отдельно.

Накопительный installer поддерживает базы 05–16. Закрой Minecraft/supervisor, распакуй архив и выполни apply.cmd либо из комплекта:

```powershell
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed" --check
py .\patch\apply_changes.py "$env:USERPROFILE\Desktop\MappetPort\MaRed"
```

Из MaRed с JDK 21:

```powershell
.\gradlew.bat build --no-parallel
.\gradlew.bat runClient
```

Clean и ручное удаление кэша не нужны. При ошибке сборки остановись. После готовности мира подожди 10–15 секунд, затем в другом PowerShell из MaRed:

```powershell
py .\tools\restart_last.py .
```

Сначала ожидаем launch_cache_capture=cached. Затем один управляемый restart без сборок/правок между ними, снова отчёт. Ожидаем launch_mode=direct_cache, fast_launch_reason=hit. Если подтверждение отклонено, достаточно конкретного кода отказа, повторные restart не требуются.

Backup создаётся автоматически; неизвестные правки блокируются. Откат при закрытой игре:

```powershell
py .\patch\apply_changes.py "C:\путь\к\MaRed" --restore "C:\путь\к\MaRed-supervisor-backup-..."
```

Проверка 16a: installer apply/repeat/byte-exact restore на базах 05–16, LF/CRLF, блокировка неизвестных правок и rollback при I/O failure. Логи прежних 19 JDK/client-double наборов относятся к Java-логике 16, которая в 16a не меняется. Эти тесты не компилировали Gradle-скрипт и потому не нашли конфликт имён. Реальный Gradle/NeoForge/Windows прогон здесь недоступен. Нет нового измеренного ускорения Minecraft. Подробности системы запуска и ограничения — docs/CAPTURE_FIX_16_RU.md.
