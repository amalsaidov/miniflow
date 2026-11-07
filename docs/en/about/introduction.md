# What MiniFlow is

MiniFlow is a small, self-hosted automation engine in the spirit of n8n, built with Spring Boot 4 and Java 21.

- A **workflow** is a JSON document: a list of **nodes** and the **connections** between them.
- Every workflow starts with a **trigger**: manual test run, webhook (HTTP call), cron schedule or a queue
  message (RabbitMQ / Kafka / Redis).
- Nodes do the work: transform data (`set`), branch (`if`), avoid duplicates (`dedupe`), call APIs (`http`),
  and talk to services (Slack, email, SQL, AI, GitHub, Google Sheets, CSV/Excel, Redis, RabbitMQ, Kafka, RSS).
- Workflows, execution history, credentials (encrypted) and variables are stored in a database
  (H2 file by default, PostgreSQL optional).
- A visual editor (Thymeleaf + Drawflow) lets you build and test workflows in the browser. UI in **English** (default) and **Turkish**.

Typical uses: receive orders or form posts and fan them out to several systems, scheduled reports,
monitoring and alerting, GitHub automation, AI-assisted triage, queue workers.
