# Running in production

Checklist:
- [ ] **Authentication.** MiniFlow has no login. Put it behind a reverse proxy with auth / VPN / SSO, or add Spring Security. Expose only `/webhook/**` publicly.
- [ ] Set `MINIFLOW_ENCRYPTION_KEY` from a secret store and back it up.
- [ ] Use PostgreSQL and keep the H2 console disabled (`MINIFLOW_H2_CONSOLE=false`, the default).
- [ ] Set `signatureSecretVar` on public webhooks that support signatures (GitHub), or add a secret token to the path/query and check it with an `if` node.
- [ ] Database credentials used by workflows should have the minimal rights (read-only for reports).
- [ ] Run a single instance: cron schedules and queue listeners run in-process (multiple instances would run schedules twice).
- [ ] Webhooks run synchronously — move slow work behind a queue (see export-request/worker).
- [ ] Use Flyway/Liquibase instead of `ddl-auto=update` if you need controlled schema changes.
- [ ] Watch the logs (`miniflow.log-node` logger for `log` nodes) and the Executions page.
