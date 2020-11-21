import wk.internal.application.ReplConsole;

import static wk.internal.application.LoggingKt.supplantSystemErr;
import static wk.internal.application.LoggingKt.supplantSystemOut;

public class Console {

    public static void main(String[] args) {
        supplantSystemOut();
        supplantSystemErr();
        ReplConsole.mainRepl(args);
    }
}
