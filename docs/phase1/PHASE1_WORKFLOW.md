# Phase 1 Workflow: Issue Management Strategy

## The Big Picture

```
Epic (Overview) → Phases Strategy (Roadmap) → Individual Issues (Implementation)
```

## Recommended Approach

### Step 1: Create the Epic Issue
**Title**: "Implement FHIRPath Support for SQL on FHIR ShareableViewDefinition"

**Content**:
- Use `.local/work/issue-shareable-view-fhirpath-support.md` as the body
- Label it as `epic` or `tracking`
- This serves as the **requirements document** and **high-level scope**
- Pin it to the repository for visibility

**Purpose**:
- Single source of truth for what we're building
- Links to specifications
- Defines the 3 phases (Phase 1, 2, 3)

---

### Step 2: Attach Phase 1 Strategy
**Options:**

**Option A (Recommended)**: Comment on Epic
- Add a comment to the epic with Phase 1 strategy
- Link to `.local/work/phase1-strategy.md` content
- Shows the 13-stage breakdown
- Easy to reference from individual issues

**Option B**: Separate Strategy Issue
- Create "Phase 1 Implementation Strategy" issue
- Link from epic
- More formal, but adds overhead

**Recommendation**: Use Option A (comment on epic)

---

### Step 3: Create Issues Incrementally (Just-in-Time)

**DO NOT create all 13 issues upfront**. Here's why:
- Strategy may evolve as we learn
- Issue details become clearer after previous stages
- Avoids stale issues sitting in backlog
- Reduces upfront planning overhead

**Instead, use this workflow:**

```
1. Create issues for Stages 1.1-1.3 (Infrastructure)
   └─ These are well-defined and prerequisites for everything else

2. Start working on Stage 1.1 → PR → Merge

3. After Stage 1.3 complete:
   └─ Create issues for Stages 1.4-1.8 (Operators)
   └─ Now we know what the test harness looks like

4. After Stage 1.8 complete:
   └─ Create issues for Stages 1.9-1.11 (Functions + Indexer)

5. After Stage 1.11 complete:
   └─ Create issues for Stages 1.12-1.13 (Quality + Docs)
```

**Benefits:**
- ✅ Issues are created when context is fresh
- ✅ Can adapt based on learnings
- ✅ Backlog stays manageable
- ✅ Focus on next 2-3 issues, not all 13

---

### Step 4: Track Implementation Order

**Use GitHub Project Board** (or Milestones):

**Option A: GitHub Project** (Recommended)
```
Columns:
- To Do (next up)
- In Progress
- In Review (PR open)
- Done

Order issues vertically in "To Do" column by stage number
```

**Option B: Milestones**
```
Milestone: "Phase 1 - Infrastructure" (Stages 1.1-1.3)
Milestone: "Phase 1 - Operators" (Stages 1.4-1.8)
Milestone: "Phase 1 - Functions" (Stages 1.9-1.11)
Milestone: "Phase 1 - Quality" (Stages 1.12-1.13)
```

**Option C: Issue References**
```
Each issue references the epic: "Part of #<epic-number>"
Epic description lists stage order
```

**Recommendation**: Use **Project Board** for visual workflow + **Milestone** for grouping

---

### Step 5: Issue Naming Convention

Use consistent naming that includes stage number:

```
[1.1] Establish Project Foundation & Code Quality Standards
[1.2] Clean Up Code for Phase 1 Scope
[1.3] Implement FHIRPath Testing Infrastructure
[1.4] Implement System Types and Literals
[1.5] Implement Boolean Operators
...
```

**Benefits:**
- Easy to see implementation order
- Sortable by stage number
- Clear connection to strategy

---

## Concrete Next Steps

### This Week: Bootstrap the Project

1. **Create Epic Issue** (5 min)
   - Title: "Implement FHIRPath Support for SQL on FHIR ShareableViewDefinition"
   - Body: Content from `issue-shareable-view-fhirpath-support.md`
   - Labels: `epic`, `phase-1`
   - Pin to repository

2. **Add Strategy as Comment** (2 min)
   - Comment on epic with Phase 1 strategy summary
   - Link to full strategy in `.local/work/phase1-strategy.md`
   - Note: "Will create issues incrementally, starting with infrastructure"

3. **Create Initial Issues** (15 min)
   - Create Stage 1.1, 1.2, 1.3 issues
   - Link each to epic: "Part of #<epic-number>"
   - Add to Project Board or Milestone

4. **Start Work on Stage 1.1** (Implementation begins!)
   - CI/CD setup
   - Documentation
   - Code quality standards

---

## Issue Lifecycle Example

```
Epic Created
  ↓
Strategy Added (comment)
  ↓
Issues 1.1-1.3 Created → Added to "Phase 1 - Infrastructure" milestone
  ↓
Work on 1.1 → PR → Review → Merge → Close Issue
  ↓
Work on 1.2 → PR → Review → Merge → Close Issue
  ↓
Work on 1.3 → PR → Review → Merge → Close Issue
  ↓
Issues 1.4-1.8 Created → Added to "Phase 1 - Operators" milestone
  ↓
Work on 1.4 → ...
  ↓
(Continue pattern)
```

---

## Benefits of This Approach

✅ **Lean**: Only create issues when needed
✅ **Adaptive**: Strategy can evolve based on learnings
✅ **Focused**: Team sees next 2-3 tasks, not overwhelming backlog
✅ **Clear**: Epic → Strategy → Issues hierarchy is explicit
✅ **Trackable**: Project board shows progress at a glance
✅ **Documented**: Strategy preserved for future reference

---

## Alternative: Create All Issues Upfront

**When this makes sense:**
- Multiple people working in parallel
- Need to assign issues in advance
- Want to estimate entire Phase 1 upfront
- Organization requires all work planned

**Tradeoffs:**
- More upfront work
- Issues may become stale
- Harder to adapt strategy
- Larger backlog to manage

**If you choose this**:
- Create all 13 issues
- Use milestones to group by stage type
- Add dependencies in issue descriptions
- Update issues as you learn

---

## Recommendation

**Start with Just-in-Time approach**:
1. Create epic + strategy (now)
2. Create 1.1-1.3 (infrastructure)
3. Create remaining issues after each milestone

**Rationale**: You're a small team, strategy may evolve, and this keeps you agile while maintaining structure.

---

## Questions?

- **Q: What if we want to work on 1.5 before 1.4?**
  - A: Fine! Order is suggested, not strict. Document deviation in PR.

- **Q: Should we close the epic when Phase 1 is done?**
  - A: No, epic covers all 3 phases. Keep open, mark Phase 1 complete.

- **Q: Where do we track Phase 2/3 planning?**
  - A: Comment on epic when Phase 1 nears completion. Create Phase 2 strategy then.
