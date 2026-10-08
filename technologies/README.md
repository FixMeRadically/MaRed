# Genesis technologies: этапы 1–5

`core` — самостоятельный модуль Java 21 без Minecraft, NeoForge и GUI.

- `expression/ExpressionEngine` — единственный парсер выражений, кэширует токены, а не результаты.
- `expression/ExpressionEnvironment` — интерфейс возможностей хоста: переменные, функции и методы.
- `storage/TextRepository` — независимое UTF-8 хранилище, версия намерения записи и проверка содержимого; старый ticket после удаления, переименования или нового save не действует. Один экземпляр обслуживает один каталог в одном процессе. Это не межпроцессная транзакция.
- `catalog/CommandTree` — неизменяемое дерево метаданных команд с алиасами, ограниченным обходом, вариантами синтаксиса и аргументами. Не содержит игровых объектов, парсеров Brigadier или исполняемых обработчиков.
- `editor/EditorLayout` — независимая геометрия рабочего интерфейса: панели, ограничение размеров, сворачивание, защита центра и нормализация некорректных ratios. Возвращает неизменяемый Frame из Rect, не рисует GUI и не вызывает Minecraft.
- `runtime/ExecutionLimits`, `FrameExecutor`, `ExecutionScope`, `OwnedActions` — используемые самим модом лимиты, машина кадров, владение ресурсами и очередь действий. Не зависят от игры.

Мод использует этот модуль через `implementation project(':genesis-core')`. Собранная библиотека упаковывается в мод через Jar-in-Jar. Старый `MaredExpr` оставлен тонким фасадом для существующих вызовов.

Из корня проекта: `./gradlew :genesis-core:build`, `./gradlew :genesis-core:publishToMavenLocal`. После локальной публикации другой Java 21 проект может подключить `implementation 'com.fixmer.genesis:genesis-core:0.1.8'` с `mavenLocal()` в repositories. Для пользователей нужен собственный Maven-репозиторий; локальная публикация не заменяет распространение.

Реализуйте `ExpressionEnvironment` и вызовите `ExpressionEngine.eval("$level >= 10 && $unlocked", host)`. Все обращения к игре и специальные функции предоставляет host. Парсер не предоставляет произвольный доступ к Minecraft сам.

API 0.1.8 экспериментальный. До публикации владелец должен определить лицензию библиотеки: в исходнике есть противоречие: gradle.properties заявляет All Rights Reserved, а neoforge.mods.toml — MIT. Этот этап не выбирает лицензию и не обещает стабильность API.

Машина исполнения уже находится в независимом core. MR-парсер, игровые события, Minecraft-адаптер и GUI остаются в моде; это следующий слой API, а не часть независимого ядра. Адаптер каталога находится в `src/main/java/com/fixmer/mared/technology/catalog/`: он копирует текущий Brigadier dispatcher и сериализует свойства зарегистрированных типов аргументов Minecraft. Детали выбранной команды вычисляются лениво, снимок обновляется при смене dispatcher/подключения, изменении корневых команд и периодически для вложенных изменений. Для модов, меняющих dispatcher на месте, предусмотрен `MinecraftCommandCatalog.refresh(connection)` на клиентском потоке.

Минимальный пример хранения (первое создание выполняется отдельно):

```java
var files = new TextRepository(Path.of("data/scripts"));
files.create("example", "initial");
var ticket = files.capture("example", "initial");
boolean saved = files.write(ticket, "updated");
```

Используйте один экземпляр репозитория на каталог, не конструируйте новый объект для каждого save. Имя не содержит путь; расширение `.txt` добавляется репозиторием. UTF-8 проверяется строго. Файл ограничен 4 МиБ. Для безопасного редактирования передавайте исходный текст в `capture`; вариант без исходного текста используется для внутренних актуальных снимков. `invalidate(name)` отменяет ожидающие записи. Minecraft-адаптер и восстановление вкладок остаются в MaRed. Библиотека по-прежнему экспериментальная, версия 0.1.8 ещё не опубликована.

В версии 0.1.3 манифест содержит `FMLModType: GAMELIBRARY` и стабильное `Automatic-Module-Name`. Это метаданные для загрузчика, а не зависимость Java-кода от Minecraft. Jar-in-Jar требует минимум 0.1.8: старое ядро 0.1.0 не должно подменять актуальное ядро в игровой сборке.


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
