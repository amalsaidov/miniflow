# Example library

All examples are in `examples/`. Each JSON file can be imported as a whole; `examples/workflows/` has one file per workflow.

| File | Contents | Needs |
|---|---|---|
| *(seeded on first start)* | `order-flow`, `contact-form`, `fx-rate-alert`, `site-health-check` | nothing |
| `basics.json` | rain alert, morning briefing, earthquake alert, crypto alert, GitHub webhook router, support ticket router, cart calculator, mini FX API | nothing (alerts go to `vars.alertWebhook`) |
| `github.json` | GitHub activity, stars tracker, weekly summary, release watch, signed webhook router, weekly SQL report → secret gist, dev journal (commits + PRs → AI → database) and stand-up note | nothing (optional `githubToken`); the gist and journal flows need `github` / `github-http`, `miniflow-db`, `gemini`, `slack` (table: `examples/sql/dev-journal.sql`) |
| `integrations.json` | RSS → Slack, daily execution report (SQL → Excel → email), contact form → email + Slack, webhook → Kafka | `slack`, `smtp`, `miniflow-db`, `kafka` |
| `realistic.json` | 10 business scenarios (next section) | see each scenario |
| `google-sheets.json` | Add a task row (webhook), open-tasks digest (read + filter), yesterday's executions → sheet (SQL → append) | `google` (+ `miniflow-db`), variable `sheetId` |
| `sql/shop-schema.sql` | demo `shop_orders` table + data | — |

## Google Sheets quick start (`examples/google-sheets.json`)
1. Create the `google` credential (service account JSON, see [Credentials](credentials.md)) and **share the spreadsheet** with the service account email as *Editor*.
2. Add two tabs to the spreadsheet with these header rows:
   - `Tasks`: `created | task | owner | due | status`
   - `Executions`: `started_at | workflow | mode | status | duration_ms`
3. Variables page → `sheetId` = the id from the sheet URL (`https://docs.google.com/spreadsheets/d/<sheetId>/edit`).
4. Import the file, open `sheets-add-task`, **▶ Test run** → a new row appears in `Tasks`. Activate it and add tasks from anywhere:
   ```bash
   curl -X POST localhost:8080/webhook/tasks -H 'Content-Type: application/json' \
     -d '{"task":"Renew SSL certificate","owner":"Bob","due":"2026-11-30"}'
   ```
5. `sheets-open-tasks-digest` reads the tab, keeps rows with `status = open` (`{{ input.rows.?[status == 'open'] }}`) and builds a
   bullet list; it ends in a `log` node — swap it for Slack, email or a gist. Mark a task done by typing `done` in the sheet.
6. `sheets-execution-log` appends yesterday's MiniFlow executions every morning (SQL rows + `columns` decide the column order).

Values are read as the sheet displays them (text), so compare as text (`status == 'open'`); appended values are entered as if typed
(`USER_ENTERED`), so dates and numbers are recognised by Sheets.

Try instantly (no network, no credentials):
```bash
curl -X POST localhost:8080/webhook/cart -H 'Content-Type: application/json' \
  -d '{"product":"Headphones","price":450,"qty":2,"coupon":"SAVE10"}'
curl "localhost:8080/webhook/fx-rate?from=USD&to=EUR"
curl -X POST localhost:8080/webhook/orders -H 'Content-Type: application/json' -d '{"orderId":42,"customer":"Alice","amount":1500}'
```
