# Genesis technologies: этапы 1–5

`core` — самостоятельный модуль Java 21 без Minecraft, NeoForge и GUI.

- `expression/ExpressionEngine` — единственный парсер выражений, кэширует токены, а не результаты.
- `expression/ExpressionEnvironment` — интерфейс возможностей хоста: переменные, функции и методы.
- `storage/TextRepository` — независимое UTF-8 хранилище, версия намерения записи и проверка содержимого; старый ticket после удаления, переименования или нового save не действует. Один экземпляр обслуживает один каталог в одном процессе. Это не межпроцессная транзакция.
- `catalog/CommandTree` — неизменяемое дерево метаданных команд с алиасами, ограниченным обходом, вариантами синтаксиса и аргументами. Не содержит игровых объектов, парсеров Brigadier или исполняемых обработчиков.
- `editor/EditorLayout` — независимая геометрия рабочего интерфейса: панели, ограничение размеров, сворачивание, защита центра и нормализация некорректных ratios. Возвращает неизменяемый Frame из Rect, не рисует GUI и не вызывает Minecraft.
- `runtime/ExecutionLimits`, `FrameExecutor`, `ExecutionScope`, `OwnedActions` — используемые самим модом лимиты, машина кадров, владение ресурсами и очередь действий. Не зависят от игры.

Мод использует этот модуль через `implementation project(':genesis-core')`. Собранная библиотека упаковывается в мод через Jar-in-Jar. Старый `MaredExpr` оставлен тонким фасадом для существующих вызовов.

Из корня проекта: `./gradlew :genesis-core:build`, `./gradlew :genesis-core:publishToMavenLocal`. После локальной публикации другой Java 21 проект может подключить `implementation 'com.fixmer.genesis:genesis-core:0.1.17'` с `mavenLocal()` в repositories. Для пользователей нужен собственный Maven-репозиторий; локальная публикация не заменяет распространение.

Реализуйте `ExpressionEnvironment` и вызовите `ExpressionEngine.eval("$level >= 10 && $unlocked", host)`. Все обращения к игре и специальные функции предоставляет host. Парсер не предоставляет произвольный доступ к Minecraft сам.

API 0.1.10 экспериментальный. До публикации владелец должен определить лицензию библиотеки: в исходнике есть противоречие: gradle.properties заявляет All Rights Reserved, а neoforge.mods.toml — MIT. Этот этап не выбирает лицензию и не обещает стабильность API.

Машина исполнения уже находится в независимом core. MR-парсер, игровые события, Minecraft-адаптер и GUI остаются в моде; это следующий слой API, а не часть независимого ядра. Адаптер каталога находится в `src/main/java/com/fixmer/mared/technology/catalog/`: он копирует текущий Brigadier dispatcher и сериализует свойства зарегистрированных типов аргументов Minecraft. Детали выбранной команды вычисляются лениво, снимок обновляется при смене dispatcher/подключения, изменении корневых команд и периодически для вложенных изменений. Для модов, меняющих dispatcher на месте, предусмотрен `MinecraftCommandCatalog.refresh(connection)` на клиентском потоке.

Минимальный пример хранения (первое создание выполняется отдельно):

```java
var files = new TextRepository(Path.of("data/scripts"));
files.create("example", "initial");
var ticket = files.capture("example", "initial");
boolean saved = files.write(ticket, "updated");
```

Используйте один экземпляр репозитория на каталог, не конструируйте новый объект для каждого save. Имя — относительный путь с разделителем `/`, до 256 символов, до 16 сегментов по 64 символа; расширение `.txt` добавляется репозиторием. UTF-8 проверяется строго. Файл ограничен 4 МиБ. Для безопасного редактирования передавайте исходный текст в `capture`; вариант без исходного текста используется для внутренних актуальных снимков. `invalidate(name)` отменяет ожидающие записи. Minecraft-адаптер и восстановление вкладок остаются в MaRed. Библиотека по-прежнему экспериментальная, версия 0.1.9 ещё не опубликована.

В версии 0.1.3 манифест содержит `FMLModType: GAMELIBRARY` и стабильное `Automatic-Module-Name`. Это метаданные для загрузчика, а не зависимость Java-кода от Minecraft. Jar-in-Jar требует минимум 0.1.9: старое ядро 0.1.0 не должно подменять актуальное ядро в игровой сборке.


Адаптер интерфейса — `src/main/java/com/fixmer/mared/technology/editor/`: `EditorWorkbench` соединяет геометрию с существующими документами и действиями; `CommandHelpBrowser` отображает MC/MR; `GenesisEditorVisuals` рисует пиксельные грани и проекцию октаэдра; `WorkbenchPreferences` сохраняет оформление. Эти классы пока зависят от Minecraft. Ядро можно использовать отдельно: `EditorLayout.calculate(width, height, .18, .29, .24, true, true, false)`; далее ваш renderer использует возвращённые прямоугольники.


В версии 0.1.4 добавлен `com.fixmer.genesis.technology.editor.TextSearch`: независимый
поиск буквального текста с переходом вперёд/назад, циклическим поиском и позициями UTF-16.
Мод использует этот же класс; сторонним редакторам не нужны классы GUI или Minecraft.
Диагностика MR пока находится в хостовом адаптере `ScriptDiagnostics` и использует
существующий парсер мода. Runtime и расширяемые обработчики теперь подключены в завершённом этапе 5.


В 0.1.5 добавлены `ScriptHeads` и `CommandInput`: общий лексический разбор
границ MR/MC и нормализованного payload с исходными UTF-16 позициями. Парсер
мода и completion используют эти же классы. Связь с живым Brigadier и Minecraft
находится в host-адаптерах `technology/catalog`; ядро по-прежнему не зависит от игры.


В 0.1.6 добавлен `ExecutionScope` — ограниченный владелец ресурсов запуска.
Он не зависит от Minecraft. `own(Kind, cancellationCallback)` возвращает lease;
обычное `lease.close()` снимает ресурс с учёта, `scope.close()` вызывает отмену.
Callbacks выполняются без блокировки scope. EXECUTOR остаётся учтённым до
его подтверждённого завершения; остальные leases освобождаются при отмене.
После отмены новые ресурсы сразу получают cancellation callback и не сохраняются.
Лимит по умолчанию 1024, меняется конструктором. Первая ошибка доступна через
snapshot().failure(); fail(error) отменяет группу. Cancellation callback должен
быть неблокирующим запросом либо безопасной очисткой реестра. Сам scope не
переносит действия в потоки игры; это обязанность host-адаптера.

```java
var scope = new com.fixmer.genesis.technology.runtime.ExecutionScope();
var lease = scope.own(com.fixmer.genesis.technology.runtime.ExecutionScope.Kind.TIMER, timer::cancel);
// Когда таймер сам завершился:
lease.close();
// Когда завершается владелец:
scope.close();
```

Host-адаптеры MaRed используют тот же scope в контексте, его forks, обработчиках
событий, таймерах и клавишных привязках. Context.setExecutionScope нельзя
использовать для замены уже установленного владельца другим. Непривязанные
контексты сохраняют прежнюю семантику; автоматический persistent loader пока
не получает отдельный scope на файл. Сетевой RPC пока не выделен в библиотеку. Очередь владения действиями независима; реализация игровых эффектов остаётся в Minecraft-адаптере.


В **0.1.7** `FrameExecutor` выполняет ваши `Instruction` и `LoopOwner` на потоке,
с которого вызывают `tick()`. Он поддерживает ограничение шагов/времени/стека,
ожидание, повтор текущей инструкции, функции, циклы, отладочную паузу и один шаг.
`MaredScriptExecutor` расширяет именно этот класс и только адаптирует MR-команды
и контекст. Нет второго неиспользуемого исполнителя.

```java
var scope = new ExecutionScope();
var executor = new FrameExecutor(
    java.util.List.of(e -> System.out.println("Hello from Genesis core")),
    ExecutionLimits.DEFAULT, scope);
executor.tick(); // ваш host вызывает tick по своему расписанию
scope.close();   // запрос отмены, безопасный и из другого потока
executor.tick(); // очистка на потоке владельца, если исполнение ещё живо
```

`OwnedActions<K,T>` объединяет независимые claims одного действия и ограниченную
очередь эффектов. `press(scope,key)`, `release(scope,key)`, `enqueue(scope,value)`;
`drain(budget,hostConsumer)` выполняет эффекты на потоке hostConsumer.
Общий лимит — число claims плюс pending actions. Ошибка эффекта отменяет
его scope. Не поддерживает откат уже начавшегося эффекта.

Для модов, зависящих от MaRed: `technology/runtime/GenesisScripts.compile(text)`
и `start(program, freshContext, limits)` возвращают Run со scope, snapshot и close.
Start вызывают на игровом потоке контекста. Не передавайте один контекст нескольким
запускам. Компиляция принимает MR, а не файл со смешанными raw-MC секциями.
`Run.close()` отменяет фоновые ресурсы; runner подтверждает очистку на следующем
тике владельца. Контекст и API не требуют открытого редактора.

Client actions (move/look/jump/attack/use/drop/swap/slot) передают intent в клиентский
адаптер. Это локальные действия игрока; сетевой протокол управления чужими клиентами
не предоставляется. После завершения кода удержание клавиши оставляет запуск
BACKGROUND до off/Stop. Клавиши других запусков и ручной ввод сохраняются.

## 0.1.8: функции и связи

В `links` добавлены ScriptExports (явные metadata в комментариях), CapabilityRegistry<T>
(неизменяемые снимки типизированных экспортов), ScriptLinks (bindings, project, dependencies/usages),
LinkedBehavior<C> (условие и отменяемое действие без перекрытия), InvocationCapabilities<C>
(явный host API выражений). Постоянная ссылка — UUID модуля + UUID экспорта + kind.
Удаление/изменение kind/сигнатуры требует исправления связи; имя файла не является её ключом.

Для Minecraft используйте адаптеры MaRed `technology/links/ScriptLibrary` и
`ScriptLinkService`; для другого host — payload реестра и LinkedBehavior.Host.
Функции импортируются без выполнения верхнеуровневых команд файла.
Порядок использования и законченный пример AI описаны в `docs/STAGE_06_SCRIPT_LINKS_AI_RU.md`.
Автоматические формы настроек и security sandbox не входят в эти контракты.


## 0.1.9: папки документов

`RepositoryPath` проверяет переносимые относительные идентификаторы. `TextRepository.entries(folder)` возвращает непосредственные файлы и папки, включая пустые; `list()` возвращает все файлы рекурсивно. `createDirectory(path)` требует существующего родителя. `rename(from, to)` перемещает один документ без перезаписи назначения, отменяя старые tickets. Символические ссылки не используются; ссылки в родительском пути отклоняются. Это защита для локального редактора, не полноценная файловая песочница против конкурентного враждебного процесса. На просмотр каталога/обход репозитория действует предел 10 000 записей.


## 0.1.10: поиск документов

`DocumentSearch.search(repository, query, mode, matchCase, cancelled)` — независимый от Minecraft поиск по сохранённым путям или тексту. Запускать на worker. Буквальный запрос, UTF-16 координаты, перекрывающиеся совпадения, нормализация CR как в редакторе. Лимиты: 128 символов запроса, 200 результатов, 4096 файлов, 8 Ми символов прочитанного текста. `limited` показывает достижение ограничения, `errors` содержит ошибки конкретных файлов. Отмена проверяется между файлами и каждые 512 символов. Обход каталога TextRepository остаётся синхронным и ограниченным 10 000 записями.

## Literal replacement (core 0.1.11)

`TextSearch.matches` validates the current UTF-16 range. `replaceAll` performs one KMP pass with non-overlapping matches, literal replacement text and a caller-supplied output-character limit. An empty query changes nothing; exceeding the limit throws before the caller mutates a document. Minecraft-specific undo and UTF-8 file limits live in `technology/editor/DocumentReplacement`.

## Projects (core 0.1.12)

`ProjectArchive` provides bounded, versioned Commands ZIP snapshots, strict UTF-8 inspection, immutable import plans and destination prefixes. `TextRepository.previewBatch/createBatch` validates collisions and creates new files with single-process rollback. Existing files are never overwritten; runtime execution and GUI are external adapters.

## Line editing (core 0.1.13)

`LineTools` transforms a bounded LF document with duplicate, move up/down and toggle-comment operations. Its immutable result carries the edited line range and UTF-16 cursor coordinates. Minecraft undo, focus and execution remain adapters outside core.

## Independent source projects (core 0.1.14)

`SourceProjects(Path)` stores UTF-8 Java/Python/Rust/C++ source projects, nested source paths and recovery drafts without Minecraft dependencies. It never compiles or executes code. The MaRed adapter/editor is in `src/main/java/com/fixmer/mared/technology/scripts`; local project root is `config/mared/script_projects`. `libraries` and `resources` are reserved directories only. No dependencies are loaded. See `docs/STAGE_08_1_SCRIPT_PROJECTS_RU.md`.

## Local Java execution (core 0.1.17)

`JavaSourceRunner` compiles bounded Java source snapshots and runs a main class in separate child JVMs. It uses the client Java runtime's `jdk.compiler` module, a clean compiler/runtime classpath and disabled annotation processing. `Limits` and `Cancellation` bound execution time, captured output and JVM heap; they are not an OS sandbox. `JavaProjectRuns` owns a single explicit local run per Scripts editor and operates separately from source-save/recovery I/O. No MR, Minecraft API, dependency loading or network protocol is involved. See `docs/STAGE_08_2_JAVA_RUNNER_RU.md`.

Java projects now snapshot explicit local JAR libraries for compilation and execution.
See docs/STAGE_08_3_JAVA_LIBRARIES_RU.md for usage and boundaries. Compiler plugins
and annotation processors are not loaded from the project classpath. No Minecraft
or network API is introduced by this stage.

Stage 8.4 snapshots Java project resources into the runtime classpath and exports
source projects as ZIP (including open edits, without saving them). See
docs/STAGE_08_4_JAVA_RESOURCES_EXPORT_RU.md. These are program resources and source
archives, not a Minecraft resource pack or a built NeoForge mod.

Stage 8.5.1: SourceProjects supports editable Gradle workspace files, tests/ and portable export. Mod version remains 0.4.0; core version 0.1.18. Gradle task execution is a later stage.

Stage 8.5.2: portable BuildTools validates local JDK/Wrapper structure without executing code. SourceProjects persists machine paths outside exports. Mod 0.4.0; core 0.1.19.

Stage 8.5.3: GradleBuildRunner provides explicit local task execution, bounded live output, stop/timeout and artifact discovery. Script editor adds Save all with recovery reconciliation. Mod 0.4.0; core 0.1.20.

Stage 8.6.1: portable immutable ProjectFileTree, cached folder projection and visible-row rendering in Scripts. Mod 0.4.0; core 0.1.21.

Stage 8.6.2: bounded GradleProjectImport copies existing projects without executing builds; full-layout export and imported Gradle-only mode. Mod 0.4.0; core 0.1.22.

Stage 8.6.2 hotfix: content fingerprints replace non-portable metadata equality during import/export. Fresh attributes, SHA-256 per file, streamed-copy verification; tree listing remains metadata-only. Mod 0.4.0; core 0.1.23.

Stage 8.6.3: NeoForgeProjectTemplate and atomic SourceProjects.createNeoForge generate an independent, pinned NeoForge starter with a validated local Wrapper. Mod 0.4.0; core 0.1.24.

## Этап 8.7.1 — рабочее пространство

core 0.1.25: `SourceProjects` создаёт пустые общие проекты, отдельные папки, выполняет rename/trash/restore и импорт обычных папок. `exportProject` экспортирует всё поддерживаемое дерево для всех языков, включая старые metadata без project_layout. `exportJava` сохраняет прежнее ограничение языка как совместимый entry point. `ProjectPath` допускает Unicode/пробелы и стандартные видимые dotfiles; формат идентификаторов Commands (`RepositoryPath`) не меняется. `ProjectFileTree.of(files, folders)` сохраняет пустые папки.

Другие системы получают `ProjectReadView` вместо изменяемого хранилища: чтение metadata/списков/текста/байтов без операций записи. Фасад читает диск, не черновики GUI; это capability API, не sandbox. MC-адаптер `ScriptProjectEditor` владеет документами и служит read-only preview другим вкладкам Logic. Полные ограничения и пользовательский маршрут описаны в `docs/STAGE_08_7_1_PROJECT_WORKSPACE_RU.md`.

## Этап 8.7.3 — копирование записей

core 0.1.26: `SourceProjects.copyEntry(id, from, to, expected[, stop])` копирует сохранённые записи внутри проекта через временное дерево. Не перезаписывает существующий путь; проверяет recovery, ожидаемые baseline, полный результирующий путь и общий лимит проекта. `ProjectReadView` не получает операций записи. MC-проводник вызывает этот API асинхронно; его внутренний буфер записей отделён от системного буфера текста. Это файловая операция без Java-рефакторинга и без блокировки внешних OS-писателей. Подробнее: `docs/STAGE_08_7_3_EXPLORER_COPY_RU.md`.

Stage 8.7.5 (mod 0.4.0, core 0.1.27): portable EditorWorkspaceState stores bounded local project/tab/explorer context independently of source recovery. The Minecraft adapter restores text files without code execution and persists context after queued saves on normal close. See docs/STAGE_08_7_5_WORKSPACE_RESTORE_RU.md for limits and lifecycle boundaries.

Stage 8.7.6 (mod 0.4.0, core 0.1.28): bounded BuildDiagnostics recognizes compiler/Gradle messages without filesystem access or Minecraft dependencies. The client adapter adds per-project diagnostics and draft-preserving source navigation. See docs/STAGE_08_7_6_BUILD_DIAGNOSTICS_RU.md for supported formats and limits.

Stage 8.7.7 (mod 0.4.0, core 0.1.29): bounded BuildArtifacts discovers root/module outputs and delivers fingerprint-checked separate copies without installing mods. GradleBuildRunner.Result retains its five-argument constructor and adds an immutable inventory. See docs/STAGE_08_7_7_BUILD_ARTIFACTS_RU.md for workflow, budgets and snapshot limitations.

Stage 8.7.8 (mod 0.4.0, core 0.1.30): bounded GradleTasks parses and filters task catalogs from explicit tasks --all requests. GradleBuildRunner.startTaskListing reuses cancellation/timeout/output limits and does not scan build artifacts. The client chooser saves a selected task without running it; reports remain separate from builds. See docs/STAGE_08_7_8_GRADLE_TASKS_RU.md.

Stage 8.7.9 (mod 0.4.0, core unchanged at 0.1.30): client adapter displays multiple cached project roots with indentation guides and distinct pixel rail icons. Explicit launch modes select Gradle build or standalone Java main-class execution; server application remains unavailable. Scripts settings have a separate reserved section. See docs/STAGE_08_7_9_WORKSPACE_UI_RU.md.
