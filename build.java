import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public final class build {

    private build() { assert false; }

    private static boolean runShellCommand(final String cwd, final Consumer<String> callback, final String...cmdLine) {
        Process process = null;
        try {
            final ProcessBuilder pb = new ProcessBuilder(cmdLine);
            pb.directory(new File(cwd));
            pb.redirectErrorStream(true);

            process = pb.start();

            final BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            for (;;) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                callback.accept(line + "\n");
            }
        } catch (final IOException ex) {
            return false;
        }
        return process.exitValue() == 0;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Invokeable {}

    public static final BuildOptions buildOptions = new BuildOptions();

    private static final class BuildOptions {
        public String srcDir         = "src";
        public String outDir         = "bin";
        public String srcFiles       = "sources.txt";
        public String compiler       = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "javac.exe").getAbsolutePath();
        public String[] compilerLine = new String[] {compiler, "-J-Xms2048m", "-J-Xmx2048m", "-J-XX:+UseG1GC", "-Xdiags:verbose", "-Xlint:all", "-Xmaxerrs", "5", "-encoding", "UTF8", "--release", "17", "-g", "-d", outDir, "-sourcepath", srcDir, "@" + srcFiles};

        public String jvmExe     = new File(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java.exe").getAbsolutePath();
        public String entryClass = "Main";
        public String[] jvmLine  = new String[] {jvmExe, "-ea", "-Xms2048m", "-Xmx2048m", "-XX:+AlwaysPreTouch", "-XX:+UseG1GC", "-cp", outDir, entryClass};
    }

    @Invokeable
    public static boolean clean() {
        try {
            final Path path = Path.of(buildOptions.outDir);

            Files.walk(path)
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);

            return true;
        } catch (final IOException ex) {
            return false;
        }
    }

    @Invokeable
    public static void run() {
        runShellCommand(".", (line) -> { System.out.print(line); }, buildOptions.jvmLine);
    }

    @Invokeable
    public static void build() {
        new File(buildOptions.srcFiles).deleteOnExit();

        try {
            // @NOTE add source files
            Files.writeString(Path.of(buildOptions.srcFiles), Path.of(buildOptions.srcDir, "Main.java").toAbsolutePath().toString());
        } catch (final IOException ex) {
            System.out.println("Failed to write sources file!");
            System.exit(1);
        }

        // @NOTE the directory will be recreated for us when we invoke the compiler with '-d'.
        if (Files.exists(Path.of(buildOptions.outDir))) {
            final boolean success = clean();
            if (!success) {
                System.out.println("Failed to cleanup previous output files!");
                System.exit(1);
            }
        }

        final StringBuilder javacOutputBuffer = new StringBuilder();
        final boolean compilationSuccess = runShellCommand(".", (line) -> { javacOutputBuffer.append(line); }, buildOptions.compilerLine);
        if (compilationSuccess) {
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build success");
        } else {
            if (!javacOutputBuffer.toString().isEmpty()) System.out.println(javacOutputBuffer.toString());
            System.out.println("Build failed");
        }
    }

    public static void main(final String[] args) {
        if (!System.getProperty("java.version").equals("17")) {
            System.out.println("build.java must be executed with java version 17");
            System.exit(1);
        }

        if (args.length == 0) {
            System.out.println("Please specify the function you wish to run!");
            System.out.println("Example: java.exe ./build.java --foobar");
            System.exit(1);
        }

        if (args.length == 1) {
            if (!args[0].startsWith("--")) {
                System.out.println("The method name must be prefixed with two dashes!");
                System.exit(1);
            }
            final String targetMethod = args[0].replace("--", "");

            for (final Method method : build.class.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && method.getName().equals(targetMethod) && method.getAnnotation(Invokeable.class) != null && method.getParameterCount() == 0) {
                    try {
                        method.invoke(null);
                        System.exit(0);
                    } catch (final Exception ex) {
                        System.out.printf("ERROR while executing the specified method: %s\n", ex.getMessage());
                        System.exit(1);
                    }
                }
            }
            System.out.printf("Failed to find the specified function '%s'.\n", targetMethod);
            System.out.println("Make sure the function you wish to execute meets the following requirements:");
            System.out.println("\t- The visibility is public");
            System.out.println("\t- It is annotated with @Invokeable");
            System.out.println("\t- Does not take any arguments");
            System.exit(1);
        } else {
            System.out.println("Too many arguments!");
            System.out.println("Example: java.exe ./build.java --build");
            System.exit(1);
        }
    }
}
