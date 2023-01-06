package vision;

import org.opencv.core.Mat;

public class Log {
    final int LEVEL;
    final String name;

    public Log(int level, String aName) {
        LEVEL = level;
        name = aName;
    }

    public void debug(int level, String msg, Mat m) {
        if (level < LEVEL)
            return;
        System.out.printf("%s %d %s %s\n", name, level, msg, m.dump());
    }

    public void debug(int level, String msg, double d) {
        if (level < LEVEL)
            return;
        System.out.printf("%s %d %s %f\n", name, level, msg, d);

    }

    public void debugmsg(int level, String msg) {
        if (level < LEVEL)
            return;
        System.out.printf("%s %d %s\n", name, level, msg);
    }
}
