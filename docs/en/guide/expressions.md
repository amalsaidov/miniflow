# Expressions

Any string parameter can contain `{{ … }}` expressions (Spring Expression Language, sandboxed).
If the whole value is a single expression, the result keeps its type (number, boolean, list, object); otherwise it is inserted as text.

| Name | Meaning | Example |
|---|---|---|
| `input` | Output of the previous node | `{{ input.total * 1.2 }}` |
| `trigger` | Data that started the run | `{{ trigger.customer.email }}` |
| `nodes` | Output of any earlier node | `{{ nodes['normalize'].orderNo }}` |
| `vars` | Variables page | `{{ vars.reportEmail }}` |
| `now` | Current time, ISO text | `{{ now }}` → `2026-11-04T10:15:00` |
| `today` | Today's date | `report-{{ today }}` |
| `nowMillis` | Epoch milliseconds | `{{ input.time > nowMillis - 600000 }}` |

**Short node names:** every node's output is also available by its id: `{{ ping.status }}` is the same as
`{{ nodes['ping'].status }}`. Ids with hyphens become camelCase: `first-alert` → `{{ firstAlert.key }}`.

**Helper functions:**

| Function | Example | Result |
|---|---|---|
| `join(list[, separator])` | `{{ join(rows.![name], '\n') }}` | one name per line (default separator `, `) |
| `default(value, fallback)` | `{{ default(input.company, '-') }}` | fallback when null / empty |
| `format(date, pattern)` | `{{ format(now, 'dd.MM.yyyy HH:mm') }}` | ISO text, date objects or epoch millis |
| `round(number[, digits])` | `{{ round(input.total * 1.2, 2) }}` | half-up rounding |
| `sum(list)` / `count(list)` | `{{ sum(rows.![total]) }}` | total / number of items |
| `json(value)` | `{{ json(input) }}` | value as JSON text |

**In the editor:** select a node to see the **Available data** panel: the outputs of the previous steps (from the last test run,
or the last execution), `trigger`, `vars` and the functions. Click a field to insert the expression at the cursor; the
**Preview** box under the parameters shows what each parameter resolves to — nothing is sent while previewing.

Patterns you will use a lot:
```text
{{ input.email == null }}                               missing fields are null, never an error
{{ input.customer?.name?.trim() }}                      safe navigation
{{ input.plan == null ? 'free' : input.plan }}          default value
{{ input.email.trim().toLowerCase() }}                  String methods
{{ input.email.split('@')[1] }}                         domain of an email
{{ input.items.size() }}                                list size
{{ input.body.?[!fork].size() }}                        filter a list (selection)
{{ input.body.![name] }}                                map a list (projection)
{{ input.title.matches('.*[A-Z]+-[0-9]+.*') }}          regex test
{{ ((input.total) * 100 + 0.5).intValue() / 100.0 }}    round to 2 decimals
{{ now.substring(0, 13) }}                              "2026-11-04T10" → once-per-hour keys
{{ input._headers['x-github-event'] }}                  webhook request header
```
Operators: `and`, `or`, `!`, `==`, `!=`, `>`, `<`, `+`, ternary `a ? b : c`.
Security: `T(...)`, `new`, static methods, `getClass()` and mutating methods are blocked; credentials are **not** readable from expressions.
