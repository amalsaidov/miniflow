# Troubleshooting / FAQ

| Symptom | Cause / fix |
|---|---|
| Webhook returns 404 "inactive" | Switch the workflow **Active** (test runs don't need it) |
| Webhook returns 404 not found | No workflow has that `path`; check the trigger parameters |
| 401 on a GitHub webhook | `githubWebhookSecret` differs from the secret in GitHub, or the request wasn't signed |
| `Credential 'x' not found` | Create a credential with exactly that name, or change the node's `credential` |
| A scheduled workflow never runs | Is it active? Check *Next run* on the Workflows page; cron uses the server time zone |
| Notifications repeat | Add a `dedupe` node; remember that test runs never record keys |
| Notification never comes after testing | The key was already recorded by a real run; change the key or clear `seen_keys` |
| Queue badge shows **not connected** | Broker unreachable or wrong credential; hover for the reason, fix, then toggle active |
| SQL column is null in expressions | Columns are lower case: `{{ input.first.order_no }}` |
| Gmail rejects login | Use an App password, port 587, starttls true |
| Google Sheets 403 | Share the sheet with the service account email as Editor; enable the Sheets API |
| AI node returns text instead of JSON | Set `"json": true` and describe the exact JSON in `system` |
| Editor palette is empty | The browser cannot reach cdn.jsdelivr.net |
| Credentials can't be decrypted after a move / *unreadable* badge | The encryption key changed (e.g. the project folder was replaced and `data/encryption.key` was lost while the PostgreSQL data stayed). Restore the old key, or edit each credential and re-enter its secrets. With PostgreSQL always set `MINIFLOW_ENCRYPTION_KEY` so the key doesn't live in the project folder |
