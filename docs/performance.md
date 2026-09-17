# Performance testing

SprintForge includes a reproducible k6 workload that exercises authenticated,
database-backed application behavior rather than a synthetic ping endpoint. Each
iteration creates a work item, reads a filtered page, adds a discussion comment,
and reads the cached board summary.

## Profiles

| Profile | Workload | Purpose |
| --- | --- | --- |
| `smoke` | 1 virtual user, 3 iterations | Validate the script and deployment |
| `load` | 10 virtual users for 2 minutes | Measure the expected steady workload |
| `stress` | Ramp from 1 to 50 virtual users | Find saturation and recovery behavior |

The load profile accepts `VUS` and `DURATION` overrides. Every profile enforces
an error rate below 1%, a p95 below 1 second, a p99 below 2 seconds, and a check
success rate above 99%. These are test thresholds, not claimed results.

## Run locally

Start the Docker Compose stack, install k6, and run:

```bash
BASE_URL=http://localhost:8080 PROFILE=smoke \
  k6 run --summary-export performance-summary.json performance/k6/sprintforge.js

BASE_URL=http://localhost:8080 PROFILE=load VUS=10 DURATION=2m \
  k6 run --summary-export performance-summary.json performance/k6/sprintforge.js
```

The GitHub Actions `Performance test` workflow runs the same script on demand
against a supplied deployment URL and preserves the raw JSON summary as an
artifact. It is deliberately manual so routine pull requests do not generate
load or persistent test data in a production environment.

## Published results

Results are added here only after a completed workflow run. A report must state
the deployment tier, date, profile, virtual users, duration, throughput, error
rate, p50, p95 and p99. Cold-start time is reported separately from the warmed
workload, because combining them would make the latency distribution misleading.

No performance result is currently claimed in this document.
