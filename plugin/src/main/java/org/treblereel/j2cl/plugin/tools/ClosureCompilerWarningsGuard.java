package org.treblereel.j2cl.plugin.tools;

import java.util.Set;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.WarningsGuard;
import org.jspecify.annotations.Nullable;

public class ClosureCompilerWarningsGuard extends WarningsGuard {

    public static final Set<String> IGNORED_WARNINGS = Set.of(
            "JSC_HIDDEN_INTERFACE_PROPERTY_MISMATCH",
            "JSC_TYPE_MISMATCH",
            "JSC_WRONG_ARGUMENT_COUNT"
    );

    public ClosureCompilerWarningsGuard() {

    }

    @Override
    public @Nullable CheckLevel level(JSError error) {
        if (IGNORED_WARNINGS.contains(error.getType().key)) {
            return CheckLevel.OFF;
        }
        return null;
    }

    @Override
    public int getPriority() {
        return 1;
    }
}
