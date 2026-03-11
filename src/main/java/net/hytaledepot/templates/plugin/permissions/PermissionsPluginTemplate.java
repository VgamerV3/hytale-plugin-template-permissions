package net.hytaledepot.templates.plugin.permissions;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class PermissionsPluginTemplate extends JavaPlugin {
  private final PermissionsPluginState state = new PermissionsPluginState();
  private final PermissionsDemoService demoService = new PermissionsDemoService();
  private final AtomicLong heartbeatTicks = new AtomicLong();
  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "hd-permissions-worker");
            thread.setDaemon(true);
            return thread;
          });

  private volatile ScheduledFuture<?> heartbeatTask;
  private volatile long startedAtEpochMillis;

  public PermissionsPluginTemplate(JavaPluginInit init) {
    super(init);
  }

  @Override
  public CompletableFuture<Void> preLoad() {
    state.setLifecycle(PermissionsPluginLifecycle.PRELOADING);
    getLogger().atInfo().log("[Permissions] preLoad -> %s", getIdentifier());
    return CompletableFuture.completedFuture(null);
  }

  @Override
  protected void setup() {
    state.setLifecycle(PermissionsPluginLifecycle.SETTING_UP);
    state.setTemplateName("Permissions");
    state.setDataDirectory(getDataDirectory().toString());

    demoService.initialize(getDataDirectory());
    state.markSetupCompleted();

    getCommandRegistry().registerCommand(new PermissionsStatusCommand(state, demoService, heartbeatTicks, this::uptimeSeconds, this::isHeartbeatActive));
    getCommandRegistry().registerCommand(new PermissionsDemoCommand(state, demoService, heartbeatTicks));

    state.setLifecycle(PermissionsPluginLifecycle.READY);
  }

  @Override
  protected void start() {
    state.setLifecycle(PermissionsPluginLifecycle.RUNNING);
    startedAtEpochMillis = System.currentTimeMillis();

    heartbeatTask =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                long tick = heartbeatTicks.incrementAndGet();
                demoService.onHeartbeat(tick);
                if (tick % 60 == 0) {
                  getLogger().atInfo().log("[Permissions] heartbeat=%d", tick);
                }
              } catch (Exception exception) {
                state.incrementErrorCount();
                getLogger().atInfo().log("[Permissions] heartbeat task failed: %s", exception.getMessage());
              }
            },
            1,
            1,
            TimeUnit.SECONDS);

    getTaskRegistry().registerTask(CompletableFuture.completedFuture(null));
  }

  @Override
  protected void shutdown() {
    state.setLifecycle(PermissionsPluginLifecycle.STOPPING);

    if (heartbeatTask != null) {
      heartbeatTask.cancel(true);
    }

    scheduler.shutdownNow();
    demoService.shutdown();
    state.setLifecycle(PermissionsPluginLifecycle.STOPPED);
  }

  private long uptimeSeconds() {
    if (startedAtEpochMillis <= 0L) {
      return 0L;
    }
    return Math.max(0L, (System.currentTimeMillis() - startedAtEpochMillis) / 1000L);
  }

  private boolean isHeartbeatActive() {
    return heartbeatTask != null && !heartbeatTask.isCancelled() && !heartbeatTask.isDone();
  }
}
