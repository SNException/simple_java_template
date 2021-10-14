import java.io.*;
import java.lang.management.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;

public final class Main {

    public static boolean isDebugMode() {
        return ManagementFactory
                        .getRuntimeMXBean()
                        .getInputArguments()
                        .contains("-ea");
    }

    public static final Logger logger = allocateLogger();

    private static Logger allocateLogger() {
        LogManager.getLogManager().reset();
        final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        final boolean isDebug = isDebugMode();
        logger.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        final ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            @Override
            public synchronized String format(final LogRecord log) {
                return String.format("[%s][%s][%s#%s]: %s\n",
                                    new SimpleDateFormat("dd.MM.YYYY HH:mm:ss:SSS").format(new Date(log.getMillis())),
                                    log.getLevel().getLocalizedName(),
                                    log.getSourceClassName(),
                                    log.getSourceMethodName(),
                                    log.getMessage()
                );
            }
        });
        consoleHandler.setLevel(isDebug ? Level.ALL : Level.SEVERE);
        logger.addHandler(consoleHandler);

        return logger;
    }

    private static void initUncaughtExceptionHandler() {
        // @NOTE We only want to apply this exception handler in dev mode so
        // we do not waste CPU time checking whether the exception is an
        // instance of AssertionError, which can not happen anyway.
        if (isDebugMode()) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                if (e instanceof AssertionError) {
                    System.err.println("--- ASSERTION FAILED ---");
                    System.err.println();
                    e.printStackTrace(System.err);

                    // @NOTE Kill the JVM immediately.
                    // I have no clue why this is not the default behaviour
                    // when triggering assert statements.
                    // That just makes them almost completely pointless.
                    Runtime.getRuntime().halt(-1);
                }

                // @NOTE fallthrough to the default behaviour
                // (found here: ThreadGroup#uncaughtException)
                if (!(e instanceof ThreadDeath)) {
                    System.err.print(
                        "Exception in thread \"" + t.getName() + "\" "
                    );
                    e.printStackTrace(System.err);
                }
            });
        }
    }

    public static void main(final String[] args) {
        if (isDebugMode()) {
            logger.log(Level.INFO, "Running with assertions enabled!");
        }

        initUncaughtExceptionHandler();

        // @NOTE Let's try to collect some garbage we have made so far
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        System.out.println("Hello, world!");
    }
}
