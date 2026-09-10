package com.fixmer.mared;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Значение MOD_ID должно совпадать с modId в META-INF/neoforge.mods.toml
@Mod(Mared.MOD_ID)
public class Mared {

    // Уникальный идентификатор мода. Должен совпадать с mod_id в gradle.properties
    public static final String MOD_ID = "mared";

    // Логгер — используется для вывода сообщений в консоль
    public static final Logger LOGGER = LogUtils.getLogger();

    // Конструктор вызывается один раз при загрузке мода.
    public Mared(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mared загружается...");
        // Здесь позже будем регистрировать блоки, предметы, команды и т.д.
    }
}