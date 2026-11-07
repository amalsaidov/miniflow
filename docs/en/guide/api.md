# HTTP API reference

| Method | Path | Description |
|---|---|---|
| GET | `/api/workflows` | All workflows |
| GET / PUT / DELETE | `/api/workflows/{id}` | Read / create-or-update / delete |
| POST | `/api/workflows/import?overwrite=true` | Import one workflow or an array |
| POST | `/api/workflows/{id}/run` | Manual run; body = trigger data (works when inactive) |
| GET | `/api/executions`, `/api/executions/{id}` | Last 50 executions / one execution |
| GET | `/api/node-types` | Registered node types |
| GET / POST | `/webhook/{path}` | Webhook trigger (workflow must be active) |
| GET | `/workflows/{id}/export`, `/workflows/export` | Download JSON |

API error messages follow the selected language (`miniflow-lang` cookie or `?lang=`).
