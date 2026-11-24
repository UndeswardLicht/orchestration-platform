# Experiments

Experiment runner and analysis for KPI-1 – KPI-6 and the NFR checks. The protocol is in
`docs/requirements/07-evaluation-plan.md`; the runner itself is task T-09 (Sprint 5).

```
experiments/
├─ ltp_experiments/   # runner: starts runs through the public API, collects results
├─ notebooks/         # analysis notebooks, one per KPI (thesis tables and charts)
└─ data/
   ├─ raw/            # raw k6 samples and API exports (gitignored, large)
   └─ results/        # small aggregated CSVs that the thesis cites (committed)
```

## Setup

```sh
cd experiments
python -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
jupyter lab
```

Run experiments only against the Demo Target (or other hosts you own).
