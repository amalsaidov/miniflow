package com.omernaci.miniflow.dashboard;

import com.omernaci.miniflow.config.MiniFlowProperties;
import com.omernaci.miniflow.credential.CredentialService;
import com.omernaci.miniflow.engine.ExecutionContext.StepLog;
import com.omernaci.miniflow.execution.Execution;
import com.omernaci.miniflow.execution.ExecutionStore;
import com.omernaci.miniflow.execution.ExecutionStore.Stat;
import com.omernaci.miniflow.queue.QueueListenerManager;
import com.omernaci.miniflow.scheduler.CronScheduler;
import com.omernaci.miniflow.variable.VariableService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.WorkflowService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    public enum Range {

        H24("24h", 24, ChronoUnit.HOURS),
        D7("7d", 7, ChronoUnit.DAYS),
        D14("14d", 14, ChronoUnit.DAYS);

        final String id;
        final int buckets;
        final ChronoUnit unit;

        Range(String id, int buckets, ChronoUnit unit) {
            this.id = id;
            this.buckets = buckets;
            this.unit = unit;
        }

        public String id() {
            return id;
        }

        static Range parse(String s) {
            for (Range r : values()) {
                if (r.id.equalsIgnoreCase(s == null ? "" : s.trim())) {
                    return r;
                }
            }
            return D7;
        }
    }

    public record Bucket(String label, String tip, long ok, long failed, double okPct, double failedPct) {
    }

    public record WorkflowStat(String id, String name, long runs, long failed, int successRate, long avgMs,
                               LocalDateTime lastRun) {
    }

    public record ModeStat(String mode, long count, double pct) {
    }

    public record Failure(String executionId, String workflowId, String workflowName, LocalDateTime startedAt,
                          String nodeId, String error) {
    }

    public record Upcoming(String workflowId, String name, String cron, LocalDateTime nextRun) {
    }

    public record Dashboard(String range, List<String> ranges,
                            long workflows, long active, long scheduled, long webhooks, long queues,
                            long runs, long succeeded, long failed, Integer successRate, long avgMs, long p95Ms,
                            List<Bucket> buckets, long maxBucket,
                            String okLine, String okArea, String failedLine,
                            List<WorkflowStat> top, List<ModeStat> modes, List<Failure> failures,
                            List<Upcoming> upcoming,
                            long listenersRunning, long listenersFailing, long credentials, long variables,
                            int retentionDays) {
    }

    private final WorkflowService workflows;
    private final ExecutionStore executions;
    private final CronScheduler scheduler;
    private final QueueListenerManager listeners;
    private final CredentialService credentials;
    private final VariableService variables;
    private final MiniFlowProperties props;

    public DashboardService(WorkflowService workflows, ExecutionStore executions, CronScheduler scheduler,
                            QueueListenerManager listeners, CredentialService credentials,
                            VariableService variables, MiniFlowProperties props) {
        this.workflows = workflows;
        this.executions = executions;
        this.scheduler = scheduler;
        this.listeners = listeners;
        this.credentials = credentials;
        this.variables = variables;
        this.props = props;
    }

    public Dashboard build(String rangeParam) {
        Range range = Range.parse(rangeParam);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstBucket = range.unit == ChronoUnit.HOURS
                ? now.truncatedTo(ChronoUnit.HOURS).minusHours(range.buckets - 1L)
                : now.truncatedTo(ChronoUnit.DAYS).minusDays(range.buckets - 1L);
        List<Stat> stats = executions.statsSince(firstBucket);

        List<Workflow> all = workflows.all();
        long active = all.stream().filter(Workflow::active).count();
        long scheduled = all.stream().filter(w -> w.active() && w.cron().isPresent()).count();
        long webhooks = all.stream().filter(w -> w.active() && w.webhookPath().isPresent()).count();
        long queues = all.stream().filter(w -> w.active() && w.listenConfig().isPresent()).count();

        long runs = stats.size();
        long failed = stats.stream().filter(s -> "ERROR".equals(s.status())).count();
        long succeeded = runs - failed;
        Integer successRate = runs == 0 ? null : (int) Math.floor(succeeded * 100.0 / runs);
        long avgMs = runs == 0 ? 0 : Math.round(stats.stream().mapToLong(Stat::durationMs).average().orElse(0));
        long[] sorted = stats.stream().mapToLong(Stat::durationMs).sorted().toArray();
        long p95 = sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1)];

        long[] ok = new long[range.buckets];
        long[] bad = new long[range.buckets];
        for (Stat s : stats) {
            int i = (int) range.unit.between(firstBucket, s.startedAt());
            if (i >= 0 && i < range.buckets) {
                if ("ERROR".equals(s.status())) {
                    bad[i]++;
                } else {
                    ok[i]++;
                }
            }
        }
        long max = 0;
        for (int i = 0; i < range.buckets; i++) {
            max = Math.max(max, ok[i] + bad[i]);
        }
        DateTimeFormatter label = range.unit == ChronoUnit.HOURS ? DateTimeFormatter.ofPattern("HH:mm")
                : DateTimeFormatter.ofPattern("dd.MM");
        DateTimeFormatter tip = range.unit == ChronoUnit.HOURS ? DateTimeFormatter.ofPattern("dd.MM HH:00")
                : DateTimeFormatter.ofPattern("EEE dd.MM");
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < range.buckets; i++) {
            LocalDateTime start = firstBucket.plus(i, range.unit);
            double scale = max == 0 ? 0 : 100.0 / max;
            buckets.add(new Bucket(start.format(label), start.format(tip), ok[i], bad[i], ok[i] * scale, bad[i] * scale));
        }

        Map<String, List<Stat>> byWorkflow = new HashMap<>();
        stats.forEach(s -> byWorkflow.computeIfAbsent(s.workflowId(), k -> new ArrayList<>()).add(s));
        List<WorkflowStat> top = byWorkflow.entrySet().stream().map(e -> {
                    List<Stat> list = e.getValue();
                    long f = list.stream().filter(s -> "ERROR".equals(s.status())).count();
                    Stat latest = list.stream().max(Comparator.comparing(Stat::startedAt)).orElseThrow();
                    String name = workflows.find(e.getKey()).map(Workflow::name).orElse(latest.workflowName());
                    return new WorkflowStat(e.getKey(), name, list.size(), f,
                            (int) Math.floor((list.size() - f) * 100.0 / list.size()),
                            Math.round(list.stream().mapToLong(Stat::durationMs).average().orElse(0)),
                            latest.startedAt());
                })
                .sorted(Comparator.comparingLong(WorkflowStat::runs).reversed()
                        .thenComparing(WorkflowStat::failed, Comparator.reverseOrder()))
                .limit(8)
                .toList();

        Map<String, Long> modeCounts = new LinkedHashMap<>();
        for (String m : List.of("webhook", "schedule", "queue", "manual")) {
            modeCounts.put(m, 0L);
        }
        stats.forEach(s -> modeCounts.merge(s.mode() == null ? "manual" : s.mode(), 1L, Long::sum));
        List<ModeStat> modes = modeCounts.entrySet().stream()
                .map(e -> new ModeStat(e.getKey(), e.getValue(), runs == 0 ? 0 : e.getValue() * 100.0 / runs))
                .toList();

        List<Failure> failures = executions.recentFailures(5).stream().map(DashboardService::failure).toList();

        List<Upcoming> upcoming = all.stream()
                .filter(w -> w.active() && w.cron().isPresent())
                .map(w -> scheduler.nextRun(w.id())
                        .map(next -> new Upcoming(w.id(), w.name(), w.cron().orElse(""), next)))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Upcoming::nextRun))
                .limit(6)
                .toList();

        long listenersFailing = all.stream()
                .filter(w -> w.active() && w.listenConfig().isPresent() && listeners.error(w.id()).isPresent())
                .count();

        return new Dashboard(range.id(), List.of(Range.H24.id(), Range.D7.id(), Range.D14.id()),
                all.size(), active, scheduled, webhooks, queues,
                runs, succeeded, failed, successRate, avgMs, p95,
                buckets, max, line(buckets, true), area(buckets), line(buckets, false),
                top, modes, failures, upcoming,
                queues - listenersFailing, listenersFailing,
                credentials.list().size(), variables.list().size(), props.executionRetentionDays());
    }

    static String line(List<Bucket> buckets, boolean ok) {
        StringBuilder sb = new StringBuilder();
        int n = buckets.size();
        for (int i = 0; i < n; i++) {
            Bucket b = buckets.get(i);
            sb.append(i == 0 ? "M" : " L").append(num((i + 0.5) * 100.0 / n)).append(',')
                    .append(num(100 - (ok ? b.okPct() : b.failedPct())));
        }
        return sb.toString();
    }

    static String area(List<Bucket> buckets) {
        int n = buckets.size();
        if (n == 0) {
            return "";
        }
        return line(buckets, true) + " L" + num((n - 0.5) * 100.0 / n) + ",100 L" + num(50.0 / n) + ",100 Z";
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static Failure failure(Execution ex) {
        StepLog step = ex.steps().stream().filter(s -> s.error() != null).reduce((a, b) -> b).orElse(null);
        String error = step == null ? "" : step.error();
        if (error.length() > 160) {
            error = error.substring(0, 157) + "…";
        }
        return new Failure(ex.id(), ex.workflowId(), ex.workflowName(), ex.startedAt(),
                step == null ? null : step.nodeId(), error);
    }
}
