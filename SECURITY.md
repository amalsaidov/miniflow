# Security policy

## Supported versions

Security fixes are made on the latest release and the `main` branch.

## Reporting a vulnerability

Please **do not** open a public issue. Report vulnerabilities privately through
[GitHub Security Advisories](https://github.com/omernaci/miniflow/security/advisories/new).

Include a description, steps to reproduce and the affected version. You will get a reply within a few days,
and a fix and advisory will be published once the issue is confirmed.

## Security model

- MiniFlow has no built-in authentication. Run it behind an authenticating reverse proxy, VPN or SSO and expose
  only `/webhook/**` to the internet.
- Credentials are encrypted with AES-GCM using `MINIFLOW_ENCRYPTION_KEY`. Keep the key out of the repository and back it up.
- Expressions run in a restricted SpEL context without type references, constructors, bean access or reflection.
- Workflows can run SQL and call HTTP endpoints with the permissions of the credentials you give them.
  Use least-privilege database users and API tokens.
