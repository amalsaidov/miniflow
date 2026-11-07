# A tour of the UI

The top bar has five pages and the **EN | TR** language switch (stored in a cookie; `?lang=tr` works on any page).

| Page | What you do there |
|---|---|
| **Dashboard** (`/dashboard`) | KPIs (workflows, executions, success rate, average/p95 duration) for the last 24 h / 7 / 14 days, an executions-over-time chart (hover a bar for numbers), triggers breakdown, most active workflows, recent failures, upcoming scheduled runs and system status |
| **Workflows** (`/`) | Search by name/id, filter by status and trigger type, pagination (10/25/50/100 per page). List with trigger badges (webhook path, cron + next run, ⇄ queue), the **Active** switch, edit / JSON export / delete, *Import JSON*, *Export all*, and *+ New workflow* |
| **Editor** (`/workflows/{id}/edit`) | Drag nodes from the palette, connect outputs to inputs, edit a node's parameters in the side panel with a **Form** (labelled fields, dropdowns, checkboxes, key–value rows) or as **JSON**, **Save**, **▶ Test run**. After a test run, nodes turn green/red; click a node to see its output |
| **Executions** (`/executions`) | Every run (manual, webhook, schedule, queue), filterable by workflow, status and trigger, paginated; click one for per-node input/output/errors |
| **Variables** (`/variables`) | Non-secret settings used as `{{ vars.name }}` |
| **Credentials** (`/credentials`) | Encrypted secrets (tokens, passwords, connection strings), referenced by name |

Editor tips:
- The **Test data** box is sent to the trigger on *Test run*. It is pre-filled from the trigger's `sample` parameter.
- Test runs work even when the workflow is **inactive**, and they don't record `dedupe` keys, so testing never "uses up" a real notification.
- `if` nodes have **true/false** outputs, `dedupe` has **new/seen**. Connect each output to where it should go; unconnected outputs simply end the run.
- The browser warns you if you leave with unsaved changes.
