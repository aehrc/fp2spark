Use the code-reviewer agent to comprehensively review all code changes in the current branch compared to main branch.

## What to do:

1. **Identify branches:**
   - Current branch: Use `git branch --show-current`
   - Base branch: main (or user can specify alternative)

2. **Invoke code-reviewer agent** with this prompt:
   ```
   Please review all code changes in this branch compared to main branch.

   Provide comprehensive analysis including:
   - Code quality issues (categorized by severity)
   - SOLID principle violations
   - Architectural concerns
   - Maintainability issues
   - Testing adequacy

   Prioritize recommendations by IMPACT and RISK:
   - HIGH IMPACT / LOW RISK (should do first)
   - HIGH IMPACT / MEDIUM RISK
   - MEDIUM IMPACT / LOW RISK
   - etc.

   For each issue, provide:
   - Specific file:line references
   - Clear explanation of the problem
   - Concrete recommendation for fix
   - Estimated effort and risk
   ```

3. **Present results** to user in organized format

## Usage:
- `/review-branch` - Reviews current branch vs main
- `/review-branch develop` - Reviews current branch vs develop
