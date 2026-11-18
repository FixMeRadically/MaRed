package com.fixmer.genesis.technology.server.remote;

import java.util.*;
/** Shared UI decisions. Missing, stale or unknown state never grants a restart button. */
public final class ServerView105 {
    private ServerView105() {}
    public record View(String phase, String operation, boolean canStart, boolean canStop,
                       boolean needsReview, boolean failed, String error) {}
    public static View of(Map<String,String> state, boolean selected, boolean working) {
        return of(state,selected,working,System.currentTimeMillis());
    }
    public static View of(Map<String,String> state,boolean selected,boolean working,long now) {
        String phase = switch(state.getOrDefault("phase", "READING")) {
            case "ACTIVE_BASELINE", "ACTIVE_UPDATED" -> "RUNNING";
            case "STOPPED_SAVED" -> "STOPPED";
            case "SAVING_OLD_SERVER" -> "STOPPING";
            case "STARTING_NEW_SERVER" -> "STARTING";
            default -> state.getOrDefault("phase", "READING");
        };
        if (!Set.of("READING", "UNSELECTED", "RUNNING", "STARTING", "STOPPING", "CHECKING",
                "RESTARTING", "STOPPED", "FAILED", "WAITING_FOR_STOP", "RECOVERY_REQUIRED",
                "BOOTSTRAP_FAILED_RESTORED", "OWNER_UNAVAILABLE", "INVALID_FOLDER").contains(phase))
            phase = "OWNER_UNAVAILABLE";
        if (Set.of("RUNNING","STARTING","STOPPING","CHECKING","RESTARTING","WAITING_FOR_STOP").contains(phase)) {
            try { long updated=Long.parseLong(state.getOrDefault("updated","0"));if(updated<=0 || now-updated>10000 || updated-now>30000)phase="OWNER_UNAVAILABLE"; }
            catch(NumberFormatException invalid) { phase="OWNER_UNAVAILABLE"; }
        }
        if (!selected) phase = "UNSELECTED";
        String error = state.getOrDefault("error", state.getOrDefault("failureCode", ""));
        boolean locked = working || "true".equals(state.get("busy"));
        boolean launchable = Set.of("STOPPED", "FAILED", "BOOTSTRAP_FAILED_RESTORED").contains(phase);
        boolean review = phase.equals("RECOVERY_REQUIRED");
        boolean failed = !error.isBlank() || Set.of("FAILED", "BOOTSTRAP_FAILED_RESTORED", "OWNER_UNAVAILABLE", "INVALID_FOLDER", "WAITING_FOR_STOP", "RECOVERY_REQUIRED").contains(phase);
        return new View(phase, phase.equals("RUNNING") ? "restart" : "start",
                selected && !locked && (launchable || phase.equals("RUNNING")),
                selected && !locked && Set.of("RUNNING", "WAITING_FOR_STOP").contains(phase), review, failed, error);
    }
    public static String hint(String code) {
        return switch(code) {
            case "SERVER104_ACCEPT_EULA" -> "eula";
            case "SERVER104_BUILD_JAR_REQUIRED", "SERVER104_CLIENT_BUILD", "SERVER104_BOOTSTRAP_VERSION", "SERVER105_AMBIGUOUS_BUILD" -> "build";
            case "SERVER104_LEGACY_WORLD_REVIEW" -> "legacy";
            case "SERVER104_JAVA21", "SERVER104_SERVER_OR_JAVA", "SERVER104_JAVA_REQUIRED" -> "java";
            case "SERVER104_PORT_BUSY" -> "port";
            case "SERVER105_MODS_CHANGED", "SERVER115_REVIEW_CHANGED" -> "mods";
            case "SERVER115_RESTORED" -> "restored115";
            case "SERVER115_RETURN_FAILED", "SERVER115_WORLD_REVIEW", "SERVER115_OPERATION_FAILED" -> "review";
            case "SERVER104_ONE_NEOFORGE_RUNTIME" -> "runtime";
            case "SERVER124_BATCH_REQUIRED", "SERVER124_BATCH_UNSUPPORTED", "SERVER124_BATCH_FORMAT", "SERVER124_BATCH_PATH", "SERVER124_CMD_REQUIRED" -> "batch124";
            case "SERVER124_BATCH_CHANGED" -> "batch_changed124";
            case "SERVER124_JAVA_AMBIGUOUS", "SERVER124_JOB_PROCESS_LIMIT" -> "review";
            case "SERVER104_DESKTOP_LAUNCH_UNAVAILABLE", "SERVER104_INDEPENDENT_PARENT_REQUIRED" -> "independent";
            case "SERVER105_PYTHON_REQUIRED" -> "python";
            case "SERVER104_MOD_FILENAME", "SERVER105_MOD_FILENAME" -> "filename";
            case "SERVER104_MOD_CASE_COLLISION", "SERVER105_MOD_CASE_COLLISION" -> "collision";
            case "SERVER105_RECOVERY_PENDING" -> "pending";
            case "SERVER104_REVIEW_REQUIRED", "SERVER104_RESTORED_WORLD_REVIEW", "SERVER104_INTERRUPTED_CHANGE_RESTORED" -> "review";
            case "SERVER105_DIRECTORY_BUSY", "DEDICATED74_SOURCE_WORLD_BUSY" -> "busy";
            case "SERVER105_FOLDER_REQUIRED", "SERVER104_SERVER_FOLDER_REQUIRED" -> "folder";
            case "BUSY", "SERVER104_REQUEST_PENDING", "DEDICATED75_REQUEST_PENDING", "DEDICATED75_BUSY" -> "queued";
            case "ACCESS88_DENIED", "DENIED", "ACCESS88_REVOKED_OR_EXPIRED" -> "permission";
            default -> "generic";
        };
    }
}
