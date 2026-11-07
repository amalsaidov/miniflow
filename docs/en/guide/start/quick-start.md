# Your first workflow in 5 minutes

Goal: a webhook that receives a newsletter sign-up, validates the email, and answers.

1. **Workflows → + New workflow**, name `Newsletter sign-up`, id `newsletter`.
2. In the editor, click the **Trigger** node and set its parameters:
   ```json
   { "path": "newsletter", "sample": { "email": "Jane@Example.com", "name": "Jane" } }
   ```
3. Add an **If** node, connect Trigger → If, parameters:
   ```json
   { "condition": "{{ input.email != null and input.email.contains('@') }}" }
   ```
4. Add a **Set** node, connect the If **true** output to it:
   ```json
   { "keepInput": false, "values": { "ok": true, "email": "{{ input.email.trim().toLowerCase() }}", "message": "Thanks {{ input.name }}!" } }
   ```
5. Add another **Set** node on the **false** output: `{ "keepInput": false, "values": { "ok": false, "error": "A valid email is required" } }`.
6. **Save**, then **▶ Test run**. Click the nodes to see their outputs.
7. Switch the workflow to **Active** and call it:
   ```bash
   curl -X POST localhost:8080/webhook/newsletter -H 'Content-Type: application/json' \
     -d '{"email":"Jane@Example.com","name":"Jane"}'
   ```
8. Make it useful: add a **Google Sheets** node (append the email) or a **Slack** node after the Set node. See [Credentials](../credentials.md).
