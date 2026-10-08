package com.fixmer.mared.commands.handbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.handbook.MaredHandbookData;
import com.fixmer.mared.handbook.MaredHandbookEntry;
import com.fixmer.mared.handbook.MaredHandbookSection;

/**
 * Руководство для вкладки commands.
 *
 * Содержит:
 *   - все Mared-команды
 *   - все Mared-переменные и builtin-функции
 *   - динамические команды подключения Minecraft
 *   - параметры из настоящего дерева команд; произвольные NBT-схемы не выдумываются
 *   - готовые рецепты (скрипты-примеры)
 */
public final class MaredCommandsHandbookData implements MaredHandbookData {

    private final List<MaredHandbookSection> sections = new ArrayList<>(16);
    private final List<MaredHandbookEntry> allEntries = new ArrayList<>(256);
    private final Map<String, MaredHandbookEntry> byId = new HashMap<>(256);

    public MaredCommandsHandbookData() {
        buildMaredBasics();
        buildMaredControl();
        buildMaredVariables();
        buildMaredEvents();
        buildMaredKeys();
        buildMaredActions();
        buildMaredExpressions();
        buildMaredBuiltins();
        buildRecipes();
    }

    @Override public String id() { return "commands"; }
    @Override public String displayName() { return "Commands"; }

    private long mcRevision=-1;
    private List<MaredHandbookEntry> mcEntries=List.of();
    private List<MaredHandbookSection> mcSections=List.of();
    /** MR documentation remains available without a world/connection. */
    public List<MaredHandbookEntry> mrEntries(){return List.copyOf(allEntries);}
    private void refreshMc() {
        long revision=com.fixmer.mared.commands.registry.MaredCommandRegistry.revision();
        if(mcRevision==revision)return;
        var group=new MaredHandbookSection("mc.commands","Команды подключения","MC",0,true);
        var entries=new ArrayList<MaredHandbookEntry>();
        for(var basic:com.fixmer.mared.commands.registry.MaredCommandRegistry.all()) {
            // List views stay cheap; detailed paths are generated only for the selected entry.
            var entry=MaredHandbookEntry.builder("mc."+basic.name,basic.name).syntax(basic.name)
                .shortDescription("Команда текущего подключения").section("MC").category(basic.category).vanilla(true).build();
            entries.add(entry);group.addEntry(entry);
        }
        mcEntries=List.copyOf(entries);mcSections=List.of(group);mcRevision=revision;
    }
    @Override public List<MaredHandbookSection> sections() {
        refreshMc();var result=new ArrayList<>(sections);result.addAll(mcSections);return List.copyOf(result);
    }

    @Override public List<MaredHandbookEntry> allEntries() {
        refreshMc();var result=new ArrayList<>(allEntries);result.addAll(mcEntries);return List.copyOf(result);
    }

    @Override public MaredHandbookEntry findById(String id) {
        if(id!=null&&id.startsWith("mc.")) {
            var info=com.fixmer.mared.commands.registry.MaredCommandRegistry.findByName(id.substring(3));
            if(info==null)return null;
            var builder=MaredHandbookEntry.builder(id,info.name).syntax(String.join("\n",info.usages))
                .shortDescription("Команда подключения Minecraft").fullDescription(info.description).section("MC").category(info.category).vanilla(true);
            for(var arg:info.arguments)builder.param(arg.value,arg.description,"Примеры типа: "+String.join(", ",arg.examples));
            for(String usage:info.usages)builder.example(usage,"Шаблон синтаксиса, не готовая команда");
            return builder.build();
        }
        return byId.get(id);
    }

    // ============================================================
    //  Хелпер: создать секцию + добавить записи
    // ============================================================

    private MaredHandbookSection section(String id, String name, String parent, int order) {
        MaredHandbookSection s = new MaredHandbookSection(id, name, parent, order, false);
        sections.add(s);
        return s;
    }

    private void add(MaredHandbookSection s, MaredHandbookEntry e) {
        s.addEntry(e);
        allEntries.add(e);
        if (e.id != null) byId.put(e.id, e);
    }

    // ============================================================
    //  Mared / Основы
    // ============================================================

    private void buildMaredBasics() {
        MaredHandbookSection s = section("mared.basics", "Основы", "Mared", 0);

        add(s, MaredHandbookEntry.builder("mared.say", "say")
            .syntax("say <текст> [scope=all|self]")
            .shortDescription("Отправить сообщение в чат")
            .fullDescription("Отправляет сообщение в игровой чат. Поддерживает подстановку переменных через $.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("text", "string", "Текст сообщения. Может содержать $переменные.")
            .paramOpt("scope", "string", "\"all\" — всем (по умолчанию), \"self\" — только инициатору.")
            .example("say \"Hello, $self\"")
            .example("say \"HP: $hp / $max_hp\"")
            .example("say \"Alert!\" scope=all")
            .see("mared.print", "mared.log", "mared.debug", "mared.mc")
            .note("В мультиплеере say выводит сообщение ЛОКАЛЬНО — иначе сервер может кикнуть за § и другие запрещённые символы.")
            .tags("chat", "multiplayer")
            .build());

        add(s, MaredHandbookEntry.builder("mared.print", "print")
            .syntax("print <текст> [scope=self|all]")
            .shortDescription("Вывести текст в лог редактора")
            .fullDescription("Пишет текст в консоль редактора. По умолчанию только в лог, с scope=self или scope=all — дублирует в чат.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("text", "string", "Текст. Поддерживает $переменные.")
            .paramOpt("scope", "string", "self | all (по умолчанию только в лог).")
            .example("print \"value: $x\"")
            .example("print \"Alert!\" scope=all")
            .see("mared.say", "mared.log", "mared.debug")
            .build());

        add(s, MaredHandbookEntry.builder("mared.log", "log")
            .syntax("log <текст>")
            .shortDescription("Записать текст в лог редактора")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("text", "string", "Текст. Может содержать $переменные.")
            .example("log \"checkpoint\"")
            .example("log \"x=$x y=$y\"")
            .see("mared.print", "mared.debug")
            .build());

        add(s, MaredHandbookEntry.builder("mared.debug", "debug")
            .syntax("debug <выражение>")
            .shortDescription("Вывести значение выражения в лог")
            .fullDescription("Печатает значение выражения. Если передан строковый литерал в кавычках — печатает как есть. Иначе печатает \"выражение = значение\".")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("expression", "any", "Что вывести. Строка в кавычках или выражение.")
            .example("debug $x")
            .example("debug $hp + $food")
            .example("debug \"reached checkpoint\"")
            .see("mared.print", "mared.log")
            .note("Имеется cooldown 200мс — одинаковые подряд сообщения подавляются.")
            .build());

        add(s, MaredHandbookEntry.builder("mared.set", "set")
            .syntax("set <имя> = <значение>")
            .shortDescription("Присвоить значение переменной")
            .fullDescription("Создаёт или перезаписывает переменную. Значение может быть числом, строкой, выражением или списком. Префикс global. делает переменную глобальной — она сохраняется между запусками.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("имя", "identifier", "Имя переменной. Без $ при объявлении.")
            .param("значение", "any", "Число, строка, выражение или список.")
            .example("set count = 5")
            .example("set name = \"Steve\"")
            .example("set global.hp = 20")
            .example("set items = [1, 2, 3]")
            .see("mared.array", "mared.global")
            .note("Для чтения используй $имя: say \"count = $count\".")
            .build());

        add(s, MaredHandbookEntry.builder("mared.array", "array")
            .syntax("array <имя> = [значения]")
            .shortDescription("Объявить массив")
            .fullDescription("Создаёт список значений. Синоним set с литералом-списком.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("имя", "identifier", "Имя массива.")
            .param("values", "list", "Список значений через запятую.")
            .example("array items = [1, 2, 3]")
            .example("array names = [\"a\", \"b\", \"c\"]")
            .see("mared.set")
            .build());

        add(s, MaredHandbookEntry.builder("mared.wait", "wait")
            .syntax("wait <число> [единица]")
            .shortDescription("Пауза в скрипте")
            .fullDescription("Приостанавливает выполнение скрипта. Единицы: ticks, ms, seconds (по умолчанию), minutes.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("amount", "number", "Сколько ждать.")
            .paramOpt("unit", "string", "ticks | ms | seconds | minutes.")
            .example("wait 3 seconds")
            .example("wait 100 ticks")
            .example("wait 500 ms")
            .build());

        add(s, MaredHandbookEntry.builder("mared.give", "give")
            .syntax("give <target> <item> [count]")
            .shortDescription("Выдать предмет игроку")
            .fullDescription("Аналог ванильного /give, но из Mared-скрипта.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("target", "selector", "@s, @a, @p, @r или имя игрока.")
            .param("item", "string", "ID предмета (minecraft:diamond).")
            .paramOpt("count", "int", "Количество (по умолчанию 1).")
            .example("give @s diamond 5")
            .example("give @s minecraft:golden_apple 1")
            .see("mared.mc")
            .build());

        add(s, MaredHandbookEntry.builder("mared.mc", "mc")
            .syntax("mc <команда>")
            .shortDescription("Выполнить ванильную команду Minecraft")
            .fullDescription("Проксирует команду на сервер. Работает так же, как /команда в чате, но из скрипта.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("command", "string", "Ванильная команда без /.")
            .example("mc time set day")
            .example("mc weather clear")
            .example("mc effect give @s speed 10 2")
            .see("mared.give")
            .note("В мультиплеере требует OP или разрешения сервера.")
            .build());

        add(s, MaredHandbookEntry.builder("mared.assert", "assert")
            .syntax("assert <условие> [\"сообщение\"]")
            .shortDescription("Проверить условие")
            .fullDescription("Если условие ложно — пишет сообщение и останавливает скрипт.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .param("condition", "bool", "Условие.")
            .paramOpt("message", "string", "Сообщение при провале.")
            .example("assert $x > 10 \"x too small\"")
            .see("mared.if")
            .build());

        add(s, MaredHandbookEntry.builder("mared.exit", "exit")
            .syntax("exit")
            .shortDescription("Остановить скрипт")
            .fullDescription("Немедленно завершает выполнение текущего скрипта. Если вызван внутри on-тела — снимает слушатель.")
            .category("Mared / Основы")
            .section("Mared")
            .mared(true)
            .example("exit")
            .see("mared.return")
            .build());
    }

    // ============================================================
    //  Mared / Управление
    // ============================================================

    private void buildMaredControl() {
        MaredHandbookSection s = section("mared.control", "Управление", "Mared", 1);

        add(s, MaredHandbookEntry.builder("mared.if", "if / elif / else")
            .syntax("if <условие> { ... } [elif <условие> { ... }] [else { ... }]")
            .shortDescription("Условное выполнение")
            .fullDescription("Выполняет блок, если условие истинно. elif — дополнительное условие. else — иначе.")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("condition", "bool", "Логическое выражение: == != < > <= >= && || ! contains")
            .example("if $hp < 5 { say \"Low HP!\" }")
            .example("if $count == 5 { say \"five\" } elif $count == 6 { say \"six\" } else { say \"other\" }")
            .see("mared.while", "mared.for")
            .build());

        add(s, MaredHandbookEntry.builder("mared.repeat", "repeat")
            .syntax("repeat <N> { ... }")
            .shortDescription("Повторить N раз")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("N", "int/expr", "Количество повторений.")
            .example("repeat 3 { say \"Tick\" }")
            .example("repeat $count { jump }")
            .see("mared.for", "mared.while")
            .build());

        add(s, MaredHandbookEntry.builder("mared.for", "for")
            .syntax("for $i = <от> to <до> { ... }   |   for $item in $array { ... }")
            .shortDescription("Цикл с диапазоном или массивом")
            .fullDescription("Две формы: числовой диапазон (включительно) или перебор массива.")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("var", "identifier", "Имя счётчика или переменной элемента.")
            .param("от / до", "int", "Диапазон (обе границы включительно).")
            .param("array", "list", "Массив для перебора.")
            .example("for $i = 1 to 5 { say $i }")
            .example("for $item in $items { say \"item: $item\" }")
            .see("mared.while", "mared.repeat")
            .build());

        add(s, MaredHandbookEntry.builder("mared.while", "while")
            .syntax("while <условие> { ... }")
            .shortDescription("Цикл, пока условие истинно")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("condition", "bool", "Проверяется перед каждой итерацией.")
            .example("while $n < 3 { set n = $n + 1 }")
            .see("mared.for", "mared.repeat")
            .note("Защита: 1 миллион итераций. Если условие никогда не станет ложным, скрипт остановится.")
            .build());

        add(s, MaredHandbookEntry.builder("mared.break", "break")
            .syntax("break")
            .shortDescription("Выйти из цикла")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .example("repeat 10 { if $i == 3 { break } }")
            .see("mared.continue")
            .build());

        add(s, MaredHandbookEntry.builder("mared.continue", "continue")
            .syntax("continue")
            .shortDescription("Перейти к следующей итерации")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .example("repeat 5 { if $i == 3 { continue } say $i }")
            .see("mared.break")
            .build());

        add(s, MaredHandbookEntry.builder("mared.func", "func")
            .syntax("func <имя>([$параметры]) { ... }")
            .shortDescription("Объявить функцию")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("name", "identifier", "Имя функции.")
            .paramOpt("params", "list", "Список параметров через запятую, с префиксом $.")
            .example("func greet($name) { say \"Hello, $name\" }")
            .example("func add($a, $b) { return $a + $b }")
            .see("mared.call", "mared.return")
            .build());

        add(s, MaredHandbookEntry.builder("mared.call", "call")
            .syntax("call <имя>(<аргументы>)")
            .shortDescription("Вызвать функцию")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .param("name", "identifier", "Имя функции.")
            .paramOpt("args", "list", "Аргументы через запятую.")
            .example("call greet(\"Steve\")")
            .example("set result = call add(2, 3)")
            .see("mared.func", "mared.return")
            .build());

        add(s, MaredHandbookEntry.builder("mared.return", "return")
            .syntax("return [<выражение>]")
            .shortDescription("Вернуть значение из функции")
            .category("Mared / Управление")
            .section("Mared")
            .mared(true)
            .paramOpt("expression", "any", "Что вернуть. Пусто — null.")
            .example("return $a + $b")
            .example("return \"done\"")
            .see("mared.func", "mared.call")
            .build());
    }

    // ============================================================
    //  Mared / Переменные
    // ============================================================

    private void buildMaredVariables() {
        MaredHandbookSection s = section("mared.vars", "Переменные", "Mared", 2);

        add(s, MaredHandbookEntry.builder("mared.self", "$self")
            .syntax("$self")
            .shortDescription("Имя игрока, запустившего скрипт")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("say \"Hello, $self\"")
            .tags("builtin", "player")
            .build());

        add(s, MaredHandbookEntry.builder("mared.world", "$world")
            .syntax("$world")
            .shortDescription("Полное имя мира (dimension)")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("say \"World: $world\"")
            .tags("builtin", "world")
            .build());

        add(s, MaredHandbookEntry.builder("mared.player_xyz", "$x $y $z")
            .syntax("$x, $y, $z")
            .shortDescription("Координаты игрока (floor)")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("say \"x=$x y=$y z=$z\"")
            .tags("builtin", "player", "coords")
            .build());

        add(s, MaredHandbookEntry.builder("mared.hp", "$hp / $max_hp")
            .syntax("$hp, $max_hp")
            .shortDescription("Текущее и максимальное HP")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $hp < 5 { say \"Low HP!\" }")
            .tags("builtin", "player")
            .build());

        add(s, MaredHandbookEntry.builder("mared.held_item", "$held_item / $held_count")
            .syntax("$held_item, $held_count, $held_name")
            .shortDescription("Предмет в главной руке")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("say \"Holding: $held_name x$held_count\"")
            .tags("builtin", "player", "inventory")
            .build());

        add(s, MaredHandbookEntry.builder("mared.dimension", "$dimension")
            .syntax("$dimension, $dimension_full")
            .shortDescription("Измерение (короткое / полное)")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $dimension == \"the_nether\" { say \"In nether!\" }")
            .tags("builtin", "world")
            .build());

        add(s, MaredHandbookEntry.builder("mared.time", "$time / $day_count")
            .syntax("$time, $day_count, $is_day, $is_night")
            .shortDescription("Игровое время суток и день")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $is_night { say \"Night time\" }")
            .tags("builtin", "world", "time")
            .build());

        add(s, MaredHandbookEntry.builder("mared.weather", "$weather")
            .syntax("$weather, $is_raining, $is_thundering")
            .shortDescription("Погода: clear / rain / thunder")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $weather == \"thunder\" { say \"Thunder!\" }")
            .tags("builtin", "world", "weather")
            .build());

        add(s, MaredHandbookEntry.builder("mared.gamemode", "$gamemode")
            .syntax("$gamemode")
            .shortDescription("Режим игры игрока")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $gamemode == \"creative\" { say \"Creative\" }")
            .tags("builtin", "player")
            .build());

        add(s, MaredHandbookEntry.builder("mared.global", "global.*")
            .syntax("$global.<имя>")
            .shortDescription("Глобальная переменная")
            .fullDescription("Сохраняется между запусками скриптов. Пишется через set global.имя = ... Читается как $global.имя.")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("set global.hp = 20")
            .example("say \"Global HP: $global.hp\"")
            .see("mared.set")
            .tags("builtin", "global")
            .build());

        add(s, MaredHandbookEntry.builder("mared.tick", "$tick")
            .syntax("$tick")
            .shortDescription("Номер текущего тика с начала сессии")
            .category("Mared / Переменные")
            .section("Mared")
            .mared(true)
            .example("if $tick % 20 == 0 { say \"1 sec\" }")
            .tags("builtin", "time")
            .build());
    }

    // ============================================================
    //  Mared / События
    // ============================================================

    private void buildMaredEvents() {
        MaredHandbookSection s = section("mared.events", "События", "Mared", 3);

        add(s, MaredHandbookEntry.builder("mared.on", "on <event>")
            .syntax("on <event> [{ ... }]")
            .shortDescription("Зарегистрировать обработчик события")
            .fullDescription("Слушает событие и выполняет блок при его возникновении. Режим add — добавить к существующим, replace — заменить (по умолчанию).")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .param("event", "identifier", "Тип события (см. список ниже).")
            .paramOpt("mode", "string", "add | replace (по умолчанию replace).")
            .example("on tick_client { say \"Tick!\" }")
            .example("on right_click add { say \"click\" }")
            .see("mared.off", "mared.every")
            .note("Список событий: right_click, left_click, middle_click, scroll_up, scroll_down, key_press, key_release, chat, tick_client, join, leave, block_break, block_place, block_interact, entity_kill, entity_hurt, player_death, respawn, item_pickup, item_crafted, dimension_change, hotbar_switch, sneak_start, sneak_end, sprint_start, sprint_end, jump, use_item, attack, first_join, player_move, health_change, hunger_change, xp_change, item_drop, gamemode_change")
            .build());

        add(s, MaredHandbookEntry.builder("mared.off", "off")
            .syntax("off <event> | every | after | all")
            .shortDescription("Снять слушателей")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .param("target", "identifier", "Имя события, every, after или all.")
            .example("off tick_client")
            .example("off all")
            .see("mared.on")
            .build());

        add(s, MaredHandbookEntry.builder("mared.every", "every")
            .syntax("every <N> ticks { ... }")
            .shortDescription("Выполнять блок каждые N тиков")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .param("N", "int", "Период в тиках (20 = 1 секунда).")
            .example("every 20 ticks { say \"1 sec\" }")
            .see("mared.after", "mared.on")
            .build());

        add(s, MaredHandbookEntry.builder("mared.after", "after")
            .syntax("after <N> ticks { ... }")
            .shortDescription("Выполнить блок один раз через N тиков")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .param("N", "int", "Задержка в тиках.")
            .example("after 100 ticks { say \"5 sec passed\" }")
            .see("mared.every")
            .build());

        add(s, MaredHandbookEntry.builder("mared.wait_until", "wait_until")
            .syntax("wait_until <условие>")
            .shortDescription("Ждать выполнения условия")
            .fullDescription("Блокирует скрипт, пока условие не станет истинным. Проверка каждый тик. Лимит 24000 тиков.")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .param("condition", "bool", "Условие.")
            .example("wait_until $hp < 5")
            .build());

        add(s, MaredHandbookEntry.builder("mared.once", "once")
            .syntax("once { ... }")
            .shortDescription("Выполнить один раз за сессию")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .example("once { say \"init\" }")
            .build());

        add(s, MaredHandbookEntry.builder("mared.first_join", "first_join")
            .syntax("first_join { ... }")
            .shortDescription("Первый вход игрока (синоним on first_join)")
            .category("Mared / События")
            .section("Mared")
            .mared(true)
            .example("first_join { say \"Welcome!\" }")
            .see("mared.on")
            .build());
    }

    // ============================================================
    //  Mared / Клавиши
    // ============================================================

    private void buildMaredKeys() {
        MaredHandbookSection s = section("mared.keys", "Клавиши", "Mared", 4);

        add(s, MaredHandbookEntry.builder("mared.bind", "bind")
            .syntax("bind <key> [add|replace|clear|block|hold|release] [{ ... }]")
            .shortDescription("Привязать скрипт к клавише")
            .fullDescription("Выполняет блок при нажатии клавиши. Режимы: add — добавить обработчик, replace — заменить (по умолчанию), clear — очистить, block — заблокировать ванильное действие, hold — выполнять каждый tick пока зажато, release — выполнить при отпускании.")
            .category("Mared / Клавиши")
            .section("Mared")
            .mared(true)
            .param("key", "string", "R, F5, Ctrl+S, Space, LeftClick, MouseMove, ...")
            .example("bind R { say \"Hi\" }")
            .example("bind W block { say \"locked\" }")
            .example("bind W hold { say \"held\" }")
            .example("bind Q clear")
            .see("mared.block", "mared.unblock", "mared.toggle")
            .build());

        add(s, MaredHandbookEntry.builder("mared.block", "block")
            .syntax("block <key>")
            .shortDescription("Заблокировать клавишу (без бинда)")
            .category("Mared / Клавиши")
            .section("Mared")
            .mared(true)
            .param("key", "string", "Имя клавиши.")
            .example("block W")
            .example("block MouseMove")
            .see("mared.unblock", "mared.bind")
            .build());

        add(s, MaredHandbookEntry.builder("mared.unblock", "unblock")
            .syntax("unblock <key>")
            .shortDescription("Снять блокировку")
            .category("Mared / Клавиши")
            .section("Mared")
            .mared(true)
            .param("key", "string", "Имя клавиши.")
            .example("unblock W")
            .see("mared.block")
            .build());

        add(s, MaredHandbookEntry.builder("mared.toggle", "toggle")
            .syntax("toggle <key>")
            .shortDescription("Переключить блокировку клавиши")
            .category("Mared / Клавиши")
            .section("Mared")
            .mared(true)
            .param("key", "string", "Имя клавиши.")
            .example("toggle W")
            .see("mared.block")
            .build());
    }

    // ============================================================
    //  Mared / Действия
    // ============================================================

    private void buildMaredActions() {
        MaredHandbookSection s = section("mared.actions", "Действия", "Mared", 5);

        add(s, MaredHandbookEntry.builder("mared.move", "move")
            .syntax("move <direction> [on|off|toggle]")
            .shortDescription("Зажать/отпустить клавишу движения")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .param("direction", "identifier", "forward | back | left | right | sneak | sprint")
            .paramOpt("mode", "identifier", "on | off | toggle (по умолчанию on)")
            .example("move forward on")
            .example("move sprint toggle")
            .example("move forward off")
            .see("mared.stop", "mared.jump")
            .build());

        add(s, MaredHandbookEntry.builder("mared.stop", "stop")
            .syntax("stop")
            .shortDescription("Сбросить все действия игрока")
            .fullDescription("Отпускает все клавиши, которые нажимал скрипт, и сбрасывает поворот.")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("stop")
            .see("mared.move")
            .build());

        add(s, MaredHandbookEntry.builder("mared.jump", "jump")
            .syntax("jump")
            .shortDescription("Прыжок")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("jump")
            .build());

        add(s, MaredHandbookEntry.builder("mared.look", "look")
            .syntax("look <yaw> <pitch>")
            .shortDescription("Установить углы камеры")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .param("yaw", "number", "Поворот по горизонтали (-180..180).")
            .param("pitch", "number", "Наклон (-90..90).")
            .example("look 90 0")
            .example("look 0 -45")
            .see("mared.look_at")
            .build());

        add(s, MaredHandbookEntry.builder("mared.look_at", "look_at")
            .syntax("look_at <x> <y> <z>")
            .shortDescription("Повернуть камеру на точку")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .param("x", "number", "X-координата цели.")
            .param("y", "number", "Y-координата цели.")
            .param("z", "number", "Z-координата цели.")
            .example("look_at 100 64 200")
            .example("look_at $x $y ($z + 5)")
            .see("mared.look")
            .build());

        add(s, MaredHandbookEntry.builder("mared.attack", "attack")
            .syntax("attack")
            .shortDescription("Атаковать цель под прицелом")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("attack")
            .build());

        add(s, MaredHandbookEntry.builder("mared.use", "use")
            .syntax("use")
            .shortDescription("Использовать предмет в руке (ПКМ)")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("use")
            .build());

        add(s, MaredHandbookEntry.builder("mared.drop", "drop")
            .syntax("drop")
            .shortDescription("Выбросить предмет из главной руки")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("drop")
            .build());

        add(s, MaredHandbookEntry.builder("mared.swap", "swap_hands")
            .syntax("swap_hands")
            .shortDescription("Поменять предметы в руках")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .example("swap_hands")
            .build());

        add(s, MaredHandbookEntry.builder("mared.slot", "select_slot")
            .syntax("select_slot <n>")
            .shortDescription("Выбрать слот хотбара")
            .category("Mared / Действия")
            .section("Mared")
            .mared(true)
            .param("n", "int", "Номер слота 0..8.")
            .example("select_slot 3")
            .example("select_slot 0")
            .build());
    }

    // ============================================================
    //  Mared / Выражения
    // ============================================================

    private void buildMaredExpressions() {
        MaredHandbookSection s = section("mared.expr", "Выражения", "Mared", 6);

        add(s, MaredHandbookEntry.builder("mared.expr.arith", "Арифметика")
            .syntax("+ - * / %")
            .shortDescription("Арифметические операции")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("set x = 2 + 3 * 4")
            .example("say \"$x / 2 = \" + ($x / 2)")
            .tags("expr")
            .build());

        add(s, MaredHandbookEntry.builder("mared.expr.compare", "Сравнения")
            .syntax("== != < > <= >=")
            .shortDescription("Операторы сравнения")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("if $hp <= 5 { say \"low\" }")
            .tags("expr")
            .build());

        add(s, MaredHandbookEntry.builder("mared.expr.logic", "Логика")
            .syntax("&& || !")
            .shortDescription("Логические операторы")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("if $hp < 5 && $food > 10 { say \"ok\" }")
            .tags("expr")
            .build());

        add(s, MaredHandbookEntry.builder("mared.expr.string", "Строки")
            .syntax("\"...\"")
            .shortDescription("Строковые литералы и конкатенация")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("set name = \"Steve\"")
            .example("say \"Hello, \" + $name")
            .tags("expr", "string")
            .build());

        add(s, MaredHandbookEntry.builder("mared.expr.list", "Списки")
            .syntax("[v1, v2, v3]")
            .shortDescription("Литералы списков и индексация")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("set items = [1, 2, 3]")
            .example("say $items[0]")
            .example("say $items[-1]")
            .tags("expr", "list")
            .build());

        add(s, MaredHandbookEntry.builder("mared.expr.methods", "Методы")
            .syntax("<объект>.<метод>(<аргументы>)")
            .shortDescription("Методы строк и списков")
            .category("Mared / Выражения")
            .section("Mared")
            .mared(true)
            .example("say $name.upper()")
            .example("say $items.size()")
            .example("$items.push(4)")
            .example("set sorted = $items.sorted()")
            .note("Методы списков: push, pop, shift, unshift, get, set, remove, size, sort, sorted, reverse, shuffled, slice, first, last, sum, avg, min, max, take, drop, distinct, flatten, map, filter, forEach, reduce. Методы строк: upper, lower, sub, find, replace, replaceAll, split, trim, len, charAt, matches.")
            .tags("expr", "method")
            .build());
    }

    // ============================================================
    //  Mared / Builtin-функции
    // ============================================================

    private void buildMaredBuiltins() {
        MaredHandbookSection s = section("mared.builtins", "Builtin-функции", "Mared", 7);

        add(s, MaredHandbookEntry.builder("mared.b.abs", "abs")
            .syntax("abs(x)")
            .shortDescription("Модуль числа")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("debug abs(-5)")
            .tags("builtin", "math")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.minmax", "min / max")
            .syntax("min(a, b)  |  max(a, b)")
            .shortDescription("Минимум / максимум двух чисел")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("debug max($hp, $food)")
            .tags("builtin", "math")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.clamp", "clamp")
            .syntax("clamp(v, lo, hi)")
            .shortDescription("Ограничить значение диапазоном")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("set hp = clamp($hp, 0, 20)")
            .tags("builtin", "math")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.random", "random")
            .syntax("random()  |  random(max)  |  random(min, max)")
            .shortDescription("Случайное число")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("set n = random(1, 6)")
            .tags("builtin", "math")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.str_num", "str / num / int / float / bool")
            .syntax("str(x) | num(x) | int(x) | float(x) | bool(x)")
            .shortDescription("Преобразование типов")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("say str(42)")
            .example("set n = int(\"15\")")
            .tags("builtin", "convert")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.len", "len")
            .syntax("len(s)")
            .shortDescription("Длина строки")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("say len($name)")
            .tags("builtin", "string")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.split_join", "split / join")
            .syntax("split(s, sep)  |  join(list, sep)")
            .shortDescription("Разбить / склеить строку")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("set parts = split(\"a,b,c\", \",\")")
            .example("say join($parts, \" | \")")
            .tags("builtin", "string")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.format", "format")
            .syntax("format(pattern, args...)")
            .shortDescription("Форматирование строки")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("say format(\"HP: %d / %d\", $hp, $max_hp)")
            .tags("builtin", "string")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.sum", "sum / avg / minList / maxList")
            .syntax("sum(list) | avg(list) | minList(list) | maxList(list)")
            .shortDescription("Агрегаты по списку")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("say str(sum($items))")
            .tags("builtin", "list")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.range", "range")
            .syntax("range(n)  |  range(a, b)")
            .shortDescription("Список чисел")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("set nums = range(1, 6)")
            .tags("builtin", "list")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.dist", "dist / dist2d")
            .syntax("dist(x1,y1,z1, x2,y2,z2)  |  dist2d(x1,z1, x2,z2)")
            .shortDescription("Расстояние между точками")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("say str(dist($x, $y, $z, 0, 64, 0))")
            .tags("builtin", "geometry")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.lerp", "lerp")
            .syntax("lerp(a, b, t)")
            .shortDescription("Линейная интерполяция")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("debug lerp(0, 10, 0.5)")
            .tags("builtin", "math")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.now", "now / timestamp")
            .syntax("now()  |  timestamp()")
            .shortDescription("Текущее время")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("debug now()")
            .tags("builtin", "time")
            .build());

        add(s, MaredHandbookEntry.builder("mared.b.uuid", "uuid")
            .syntax("uuid()")
            .shortDescription("Случайный UUID")
            .category("Mared / Builtin-функции")
            .section("Mared")
            .mared(true)
            .example("debug uuid()")
            .tags("builtin", "random")
            .build());
    }

    // ============================================================
    //  Vanilla / Команды
    // ============================================================

    private void buildRecipes() {
        MaredHandbookSection s = section("mared.recipes", "Рецепты", "Примеры", 0);

        add(s, MaredHandbookEntry.builder("mared.recipe.welcome", "Приветствие при входе")
            .syntax("first_join { ... }")
            .shortDescription("Отправить приветствие при первом входе игрока")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("first_join {\n    wait 2 seconds\n    say \"Welcome, $self!\"\n    say \"Use /mared to open the editor\"\n}",
                     "Скрипт целиком — вставь в файл")
            .see("mared.first_join", "mared.say", "mared.wait")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.lowhp", "Предупреждение при низком HP")
            .syntax("on tick_client { if $hp < 5 { ... } }")
            .shortDescription("Ругаться, когда HP падает ниже 5")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("on tick_client {\n    if $hp > 0 && $hp < 5 {\n        say \"§cLow HP!\"\n    }\n}",
                     "§ может кикнуть в мультиплеере — используй без него")
            .see("mared.on", "mared.if", "mared.hp")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.walkbot", "Бот-ходьба")
            .syntax("bind R { move forward on; wait 2 seconds; stop }")
            .shortDescription("Нажать R — идти 2 секунды вперёд")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("bind R {\n    move forward on\n    wait 2 seconds\n    move forward off\n}",
                     "Управляется клавишей R")
            .see("mared.bind", "mared.move", "mared.wait")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.responder", "Авто-ответчик")
            .syntax("on chat { if $message contains \"hi\" { say \"Hi!\" } }")
            .shortDescription("Отвечать на приветствие в чате")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("on chat {\n    if $message contains \"hi\" {\n        say \"Hello there!\"\n    }\n}",
                     "В мультиплеере — отвечай scope=self")
            .see("mared.on", "mared.if")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.counter", "Счётчик убийств")
            .syntax("on entity_kill { set global.kills = $global.kills + 1 }")
            .shortDescription("Считать убийства мобов в глобальной переменной")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("on entity_kill {\n    set global.kills = $global.kills + 1\n    say \"Kills: $global.kills\"\n}",
                     "global сохраняется между запусками")
            .see("mared.on", "mared.set", "mared.global")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.cycle_slot", "Перебор слотов")
            .syntax("repeat 9 { select_slot $i; wait 0.3 seconds }")
            .shortDescription("Пробегаться по всем 9 слотам хотбара")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("for $i = 0 to 8 {\n    select_slot $i\n    wait 5 ticks\n}",
                     "Перебирает слоты 0..8")
            .see("mared.for", "mared.slot")
            .build());

        add(s, MaredHandbookEntry.builder("mared.recipe.escape", "Авто-побег")
            .syntax("on health_change { if $new_hp < 5 { move sprint on; look $x $z } }")
            .shortDescription("При низком HP — отбежать и развернуться")
            .category("Примеры / Рецепты")
            .section("Примеры")
            .mared(true)
            .example("on health_change {\n    if $new_hp < 5 {\n        move sprint on\n        wait 3 seconds\n        move sprint off\n    }\n}",
                     "Работает на клиенте, без мода на сервере")
            .see("mared.on", "mared.move")
            .build());
    }

    // ============================================================
    //  Хелпер для Vanilla-записей
    // ============================================================

}
