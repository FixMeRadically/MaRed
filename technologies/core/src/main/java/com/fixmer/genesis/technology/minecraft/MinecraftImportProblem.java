package com.fixmer.genesis.technology.minecraft;

import java.util.List;
import java.util.Objects;

/** Actionable import problem with suggested resolutions for UI. */
public record MinecraftImportProblem(
        String code,
        String message,
        Severity severity,
        List<Solution> solutions
) {
    public enum Severity { INFO, WARNING, ERROR }

    public enum Solution {
        AUTO_DETECT,
        SELECT_MANUALLY,
        IGNORE,
        RETRY,
        USE_COPY_MODE,
        USE_LINK_MODE
    }

    public MinecraftImportProblem {
        Objects.requireNonNull(code);
        Objects.requireNonNull(message);
        Objects.requireNonNull(severity);
        solutions = solutions == null ? List.of() : List.copyOf(solutions);
    }

    public static MinecraftImportProblem error(String code, String message, Solution... solutions) {
        return new MinecraftImportProblem(code, message, Severity.ERROR, List.of(solutions));
    }

    public static MinecraftImportProblem warning(String code, String message, Solution... solutions) {
        return new MinecraftImportProblem(code, message, Severity.WARNING, List.of(solutions));
    }
}
