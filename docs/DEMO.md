# Demo walkthrough (written in spec 006, kept current)

Goal: a reproducible, recordable end-to-end demo.
1. Start local infra (`docker compose ... up -d`) and the app.
2. Run the sandbox generator to publish 5 failures of different classes.
3. Open Jaeger and follow one message from consumption to decision.
4. From an MCP client: list failures, read a diagnosis, approve a reprocess.
5. Show the message republished and the audit trail.
   Record the session (asciinema or screen capture) and link it from README.