package com.fixmer.mared;
import org.junit.jupiter.api.Test;
/** Reuse the standalone headless regression suite from the conventional check lifecycle. */
public class RuntimeIntegrationTest {
    @Test public void runtimeRegressions() { RuntimeIntegrationChecks.main(new String[0]); }
}
