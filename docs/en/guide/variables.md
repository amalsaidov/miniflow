# Variables

Variables hold non-secret settings: email recipients, sheet ids, URLs, repo names, thresholds. Use them as `{{ vars.name }}`.
- Edit them on the **Variables** page; changes apply immediately.
- On first start, `miniflow.vars[...]` from the configuration (`application.properties` or environment variables) are copied into the table. Values only defined in properties
  or environment (`MINIFLOW_VARS_REPORTEMAIL=…`) appear with the label *application.properties*; editing one copies it into the table (the table wins).
- Names: letters, digits and `_`, starting with a letter or `_`. Case-insensitive.
- Values are visible in the UI — **tokens and passwords belong in Credentials**.

Variables used by the examples: `reportEmail`, `healthCheckUrl`, `watchUrl`, `alertWebhook`, `githubUser`, `githubRepo`,
`githubWatchRepo`, `githubToken` (optional), `githubWebhookSecret`, `sheetId`, `leadsSheetId`, `supportSheetId`.
