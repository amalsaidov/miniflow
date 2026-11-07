package com.omernaci.miniflow.scheduler;

import com.omernaci.miniflow.execution.Execution;
import com.omernaci.miniflow.execution.ExecutionService;
import com.omernaci.miniflow.workflow.Workflow;
import com.omernaci.miniflow.workflow.WorkflowChangedEvent;
import com.omernaci.miniflow.workflow.WorkflowService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class CronScheduler {

    private static final Logger log = LoggerFactory.getLogger(CronScheduler.class);

    private final WorkflowService workflows;
    private final ExecutionService executions;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, Scheduled> jobs = new ConcurrentHashMap<>();

    private record Scheduled(String cron, ScheduledFuture<?> future) {
    }

    public CronScheduler(WorkflowService workflows, ExecutionService executions) {
        this.workflows = workflows;
        this.executions = executions;
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("miniflow-cron-");
        scheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAll() {
        workflows.active().forEach(this::reschedule);
        log.info("Scheduled workflows: {}", jobs.size());
    }

    @EventListener
    public void onChange(WorkflowChangedEvent event) {
        Optional<Workflow> wf = workflows.find(event.workflowId());
        if (wf.isPresent()) {
            reschedule(wf.get());
        } else {
            cancel(event.workflowId());
        }
    }

    public Optional<LocalDateTime> nextRun(String workflowId) {
        Scheduled s = jobs.get(workflowId);
        if (s == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CronExpression.parse(s.cron()).next(LocalDateTime.now()));
    }

    private synchronized void reschedule(Workflow wf) {
        Optional<String> cron = wf.active() ? wf.cron().map(CronSupport::normalize) : Optional.empty();
        Scheduled current = jobs.get(wf.id());

        if (cron.isEmpty()) {
            cancel(wf.id());
            return;
        }
        if (current != null && current.cron().equals(cron.get())) {
            return;
        }
        cancel(wf.id());
        String workflowId = wf.id();
        ScheduledFuture<?> future = scheduler.schedule(() -> fire(workflowId), new CronTrigger(cron.get()));
        jobs.put(workflowId, new Scheduled(cron.get(), future));
        log.info("Workflow '{}' scheduled: {}", workflowId, cron.get());
    }

    private synchronized void cancel(String workflowId) {
        Scheduled s = jobs.remove(workflowId);
        if (s != null) {
            s.future().cancel(false);
            log.info("Workflow '{}' unscheduled", workflowId);
        }
    }

    private void fire(String workflowId) {
        try {
            workflows.find(workflowId).filter(Workflow::active).ifPresent(wf -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("firedAt", LocalDateTime.now().toString());
                data.put("cron", wf.cron().orElse(""));
                Execution ex = executions.run(wf, data, "schedule");
                if ("ERROR".equals(ex.status())) {
                    log.warn("Scheduled workflow '{}' finished with an error (execution {})", workflowId, ex.id());
                }
            });
        } catch (Exception e) {
            log.error("Scheduled workflow '{}' could not be run", workflowId, e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
