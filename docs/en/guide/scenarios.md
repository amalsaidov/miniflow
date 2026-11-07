# Realistic scenarios step by step

`examples/realistic.json` models a small online shop and its engineering team. All 10 workflows are imported **inactive**.

## One-time setup
1. **Import:** Workflows → *Import JSON* → choose `examples/realistic.json` → *Import*.
2. **Demo shop database:** start MiniFlow with `MINIFLOW_H2_CONSOLE=true`, open `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/miniflow`, user `sa`, empty password),
   paste `examples/sql/shop-schema.sql`, run. (With PostgreSQL, run it in your Postgres database instead.)
3. **Credentials:** create the ones each scenario needs, using the names in [Credentials](credentials.md) (`shop-db`, `smtp`, `slack`, …).
4. **Variables:** set `reportEmail`, `healthCheckUrl`, `githubRepo`, `leadsSheetId`, `supportSheetId` as needed.
5. Open a workflow, **▶ Test run** with the sample data, inspect each node, then switch it **Active**.

| Workflow | Trigger | Credentials | Variables |
|---|---|---|---|
| `order-pipeline` | webhook `/webhook/shop-orders` | shop-db, rabbitmq, slack, smtp | — |
| `support-triage` | webhook `/webhook/support` | openai, github, slack, smtp, google | githubRepo, supportSheetId |
| `uptime-incident` | every 5 min | github, slack | healthCheckUrl, githubRepo |
| `daily-sales-report` | weekdays 07:45 | shop-db, smtp, slack | reportEmail |
| `lead-capture` | webhook `/webhook/leads` | google, slack | leadsSheetId |
| `payment-failure-monitor` | Kafka `payments.events` | kafka, redis, slack | — |
| `release-watch-ai` | daily 09:00 | openai, slack, github | githubRepo |
| `pr-issue-sync` | webhook `/webhook/github-pr` (signed) | github, slack | githubWebhookSecret |
| `export-request` | webhook `/webhook/exports` | redis | — |
| `export-worker` | Redis list `export-jobs` | redis, shop-db, smtp | — |

## `order-pipeline` — e-commerce order intake
```
webhook → valid & paid? ─✗→ rejected
            ✓ → normalize (trim, lower-case email, currency) → already processed? ─seen→ duplicate-ignored
                                                                  new → INSERT shop_orders → RabbitMQ fulfillment.orders
                                                                        → ≥ 1000? ─✓→ Slack #sales → confirmation email → accepted
                                                                                  ─✗────────────────→ confirmation email → accepted
```
Why it's realistic: shops retry webhooks, so `dedupe` on `order-{{ orderNo }}` makes processing idempotent; the warehouse consumes a durable queue; only big orders ping sales.
```bash
curl -X POST localhost:8080/webhook/shop-orders -H 'Content-Type: application/json' -d '{
  "orderNo":"ORD-1001","currency":"eur","total":1250.0,"paymentStatus":"paid",
  "customer":{"name":"Alice Smith","email":"Alice@Example.com"},
  "items":[{"sku":"HP-200","name":"Headphones","qty":2,"price":450},{"sku":"CB-10","name":"USB-C cable","qty":1,"price":350}]}'
```
Send it twice: the second answer is `"status": "duplicate-ignored"`. Check the row in H2 (`SELECT * FROM shop_orders`) and the message in the RabbitMQ UI.

## `support-triage` — AI triage of support requests
The website's contact form posts to `/webhook/support`. The AI node returns `{category, urgency, sentiment, summary}` as JSON.
- **Urgent** → GitHub issue in `vars.githubRepo` (labels `support`, category, `urgent`) → Slack page with the issue link.
- **Not urgent** → a row in the Google Sheet `Tickets` tab (`vars.supportSheetId`).
- Both → acknowledgement email to the customer; the webhook answers with category/urgency/ticket.

Adjust `projectKey`, the issue type and the system prompt to your setup. Create a `Tickets` tab in the sheet first.

## `uptime-incident` — monitoring with incident issues
Every 5 minutes `GET {{ vars.healthCheckUrl }}` with `failOnError: false`. When it's down (non-2xx or no connection), a GitHub issue
(labels `incident`, `monitoring`) is opened in `vars.githubRepo` and Slack is paged with the link — **at most once per hour**
thanks to `dedupe` with `incident-{{ now.substring(0, 13) }}`.

## `daily-sales-report` — SQL → Excel → email + Slack
On weekdays at 07:45: summary query (orders, revenue, average order of *yesterday*), full order list → `spreadsheet` (xlsx) →
email to `vars.reportEmail` with the Excel attached → Slack one-liner. With the demo data you get "3 orders, 1759.90 revenue".
Point `shop-db` to a **read-only** user on your real database.

## `lead-capture` — landing page → CRM sheet
`POST /webhook/leads` (or an HTML form) → normalize (company from the email domain if missing, `utm_source`) → `dedupe` by email →
append to the `Leads` tab of `vars.leadsSheetId` (column order: receivedAt, name, email, company, plan, source) → Slack #sales.
Repeat sign-ups only get the thank-you response.

## `payment-failure-monitor` — Kafka + Redis counter
Listens to the `payments.events` topic. For each `FAILED` event it increments the Redis key `payment-failures:<hour>`;
when the count reaches 5 in an hour, the payments team gets **one** Slack alert for that hour.
Test it by producing messages:
```bash
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic payments.events
{"paymentId":"pay_1","orderNo":"ORD-1001","status":"FAILED","reason":"card_declined"}
```
Or use the `kafka-event-publisher` example from `integrations.json`.

## `release-watch-ai` — dependency release digest
Daily: reads the Spring Boot releases Atom feed, `dedupe` by entry id, asks the AI for a 3-bullet digest (breaking changes first),
posts it to Slack and opens a GitHub upgrade issue in `vars.githubRepo`. Change the RSS URL for any GitHub project (`https://github.com/<owner>/<repo>/releases.atom`).

## `pr-issue-sync` — GitHub pull requests → comment on the linked issue + Slack
GitHub repo → Settings → Webhooks: payload URL `https://<public-url>/webhook/github-pr`, content type `application/json`,
secret = the `githubWebhookSecret` variable, event **Pull requests**. When the PR title or description references an issue
(`Fixes #142`), that issue gets a comment "Pull request #77 merged by @…" and Slack gets a one-liner with the PR link.
Other PRs are ignored. Locally, expose the app with `ngrok http 8080`
or `gh webhook forward --repo=<owner>/<repo> --events=pull_request --url=http://localhost:8080/webhook/github-pr`.

## / 11.10 `export-request` + `export-worker` — asynchronous jobs
A classic producer/consumer pair: the request webhook answers **immediately** ("being prepared") and pushes a job to the Redis list
`export-jobs`; the worker (activate it too) pops jobs, queries the customer's orders, builds an Excel file and emails it.
```bash
curl -X POST localhost:8080/webhook/exports -H 'Content-Type: application/json' -d '{"email":"alice@example.com"}'
```
Use this pattern whenever work is slow (reports, AI, big files) so webhooks stay fast.
