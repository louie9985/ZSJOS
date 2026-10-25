package maintenance;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** One-shot, explicitly authorized local maintenance; no transformer or listening endpoint. */
public final class TransferMaintenanceAgent {
    public static void agentmain(String argument, Instrumentation instrumentation) throws Exception {
        Path config = Path.of(argument).toAbsolutePath();
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(config)) { properties.load(stream); }
        Path output = Path.of(properties.getProperty("result"));
        try {
            Class<?> spring = null;
            for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                if (loaded.getName().equals("cn.hutool.extra.spring.SpringUtil")) { spring = loaded; break; }
            }
            if (spring == null) throw new IllegalStateException("Existing Spring context unavailable");
            Object context = spring.getMethod("getApplicationContext").invoke(null);
            if (context == null) throw new IllegalStateException("Existing Spring context unavailable");
            // Only the reviewed recovery helper is shadowed to add the business-lock callback.
            // All framework, engine and application types are the running application's types.
            try (var loader = new URLClassLoader(new URL[]{Path.of(properties.getProperty("classes")).toUri().toURL()},
                    spring.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("maintenance.") || name.startsWith(
                            "cn.iocoder.yudao.module.bpm.service.task.BpmStartUserTaskRecovery")) {
                        synchronized (getClassLoadingLock(name)) {
                            Class<?> loaded = findLoadedClass(name);
                            if (loaded == null) loaded = findClass(name);
                            if (resolve) resolveClass(loaded);
                            return loaded;
                        }
                    }
                    return super.loadClass(name, resolve);
                }
            }) {
                Class<?> runner = loader.loadClass("maintenance.TransferMaintenanceRunner");
                ClassLoader before = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(spring.getClassLoader());
                    runner.getMethod("run", Object.class, Properties.class).invoke(null, context, properties);
                } finally { Thread.currentThread().setContextClassLoader(before); }
            }
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            // Do not export arbitrary exception messages containing business payloads or credentials.
            StringBuilder diagnostic = new StringBuilder("FAILED: ").append(cause.getClass().getName()).append('\n');
            for (StackTraceElement frame : cause.getStackTrace()) diagnostic.append(frame).append('\n');
            Files.writeString(output, diagnostic.toString());
        }
    }
}
