package com.fixmer.mared.gui2.studio.events;

/**
 * Порядок вызова подписчиков.
 *
 * 0.3.1: без приоритетов порядок был случайный (HashMap). С ними:
 *   HIGH  — вызывается первым
 *   NORMAL — по умолчанию
 *   LOW   — вызывается последним
 *
 * Пример: Inspector должен обновиться ДО того, как Console запишет
 * "opened: x" — чтобы пользователь сначала увидел новое содержимое
 * справа, а потом уже лог.
 */
public enum StudioPriority {
    HIGH,
    NORMAL,
    LOW
}