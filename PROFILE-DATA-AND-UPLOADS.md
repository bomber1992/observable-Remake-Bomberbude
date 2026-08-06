# Profile Data and Uploads

Observable - Remake uploads a completed profiling report to:

https://obs.bombersbude.de/

The report contains Minecraft server profiling information needed for analysis,
including tick timings, entities, block entities, chunks, execution traces,
diagnostics and the player name that started the profiling session when one is
available. Sign and hanging-sign targets are intentionally excluded from
profiling and therefore are not included in uploaded reports.

The upload implementation does not intentionally collect passwords,
authentication tokens, private chat messages or arbitrary files from the host.
The upload endpoint is public and does not embed an API key in the mod. The
service applies strict Observable-profile validation, compressed and expanded
size limits, entry limits and per-IP rate limiting.

Source code:
https://github.com/bomber1992/observable-Remake-Bomberbude

Profile viewer:
https://obs.bombersbude.de/
