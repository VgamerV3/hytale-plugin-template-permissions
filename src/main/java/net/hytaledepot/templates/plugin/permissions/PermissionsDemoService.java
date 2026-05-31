package net.hytaledepot.templates.plugin.permissions;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class PermissionsDemoService {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> permissionsBySender = new ConcurrentHashMap<>();
  private volatile Path dataDirectory;

  public void initialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    permissionsBySender.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();

  }

  public void recordExternalEvent(String key) {
    actionCounters.computeIfAbsent(String.valueOf(key), item -> new AtomicLong()).incrementAndGet();
  }

  public String applyAction(PermissionsPluginState state, String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = state.toggleDemoFlag();
      return "[Permissions] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[Permissions] " + diagnostics();
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[Permissions] " + domainResult;
    }

    return "[Permissions] unknown action='" + normalizedAction + "' (try: info, toggle, sample, grant-demo, revoke-demo, check-demo)";
  }

  public String describeLastAction(String sender) {
    return lastActionBySender.getOrDefault(String.valueOf(sender), "none");
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public String diagnostics() {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    int totalGrants = permissionsBySender.values().stream().mapToInt(Set::size).sum();
    return "ops=" + operationCount()
        + ", principals=" + permissionsBySender.size()
        + ", grants=" + totalGrants
        + ", dataDirectory=" + directory;
  }

  public void shutdown() {
    permissionsBySender.clear();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    Set<String> grants = grantsFor(sender);
    if ("sample".equals(action) || "grant-demo".equals(action)) {
      grants.add("hd.example.use");
      grants.add("hd.example.manage");
      return "granted " + new TreeSet<>(grants);
    }
    if ("revoke-demo".equals(action)) {
      grants.remove("hd.example.manage");
      return "remaining grants=" + new TreeSet<>(grants);
    }
    if ("check-demo".equals(action)) {
      return "hd.example.use=" + grants.contains("hd.example.use") + ", hd.example.manage=" + grants.contains("hd.example.manage");
    }
    return null;
  }

  private Set<String> grantsFor(String sender) {
    return permissionsBySender.computeIfAbsent(String.valueOf(sender).toLowerCase(), key -> ConcurrentHashMap.newKeySet());
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }
}
