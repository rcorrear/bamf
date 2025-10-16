# Constitution Update Checklist

When amending the constitution (`.specify/memory/constitution.md`), ensure all dependent documents are refreshed so day-to-day workflows reflect the latest principles.

## Templates to Update

- [ ] `.specify/templates/plan-template.md` — Constitution Check list and gating prompts.
- [ ] `.specify/templates/spec-template.md` — User story, requirements, and event model guidance.
- [ ] `.specify/templates/tasks-template.md` — Task formatting, Rama event work, telemetry, and testing expectations.
- [ ] `.codex/prompts/speckit.plan.md` — Planning command instructions.
- [ ] `.codex/prompts/speckit.tasks.md` — Task generation guidance.
- [ ] `README.md` / `docs/` quickstarts — Runtime expectations and onboarding docs.

## Principle-Specific Follow-Ups

### P1. Specification-First Delivery
- [ ] Confirm templates require spec/plan/tasks before coding.
- [ ] Update checklists that reference gating or approval flows.

### P2. Event-Driven Persistence
- [ ] Ensure specs/plans/tasks call out event names and payload contracts.
- [ ] Verify Rama modules and persistence docs reflect new events.

### P3. Polylith Component Integrity
- [ ] Reaffirm interface boundaries in architecture docs.
- [ ] Update DI/`donut-party.system` examples if wiring rules change.

### P4. Telemetry-Backed Operations
- [ ] Add or adjust Telemere logging/metrics requirements in templates.
- [ ] Update observability runbooks and dashboards references.

### P5. Tested, Repeatable Delivery
- [ ] Confirm templates cite 80% coverage minimum and test ordering.
- [ ] Update CI instructions or additional test commands if needed.

## Validation Steps

1. **Before finalizing constitution changes:**
   - [ ] All templates reference the new or modified requirements.
   - [ ] Examples and sample tasks mention required events and telemetry.
   - [ ] No contradictions remain between constitution, templates, and docs.

2. **After template updates:**
   - [ ] Walk through a sample plan/tasks workflow to ensure gates are actionable.
   - [ ] Verify persistence stories include events and idempotency guidance.
   - [ ] Check that documents stand alone without needing the constitution open.

3. **Version Tracking:**
   - [ ] Update the constitution version line.
   - [ ] Note version bumps in template comments if applicable.
   - [ ] Record amendments in project changelogs when needed.

## Template Sync Status

Last sync check: 2025-10-16  
- Constitution version: 1.0.0  
- Templates aligned: ✅ (plan/spec/tasks refreshed 2025-10-16)

---

*Use this checklist each time the constitution changes to keep the ecosystem in sync.*