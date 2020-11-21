import wk.internal.viewer.ViewerUi;

import static wk.internal.application.LoggingKt.supplantSystemErr;
import static wk.internal.application.LoggingKt.supplantSystemOut;


public class Viewer {

    public static void main(String[] args) {
        supplantSystemOut();
        supplantSystemErr();
        ViewerUi.mainViewer();
    }
}
