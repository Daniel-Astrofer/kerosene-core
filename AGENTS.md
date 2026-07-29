# Agent rules

- Keep Auth and KFE deployable as separate processes and images.
- Do not introduce shared database ownership between services.
- Protocol changes must be backward-compatible and coordinated through
  `kerosene-contracts`.
- Never commit credentials, JWT secrets, macaroons, seeds or production data.
- Run Gradle verification and adapter tests before pushing.
- Do not remove the local contracts compatibility module until all consumers use
  a published contracts version.
