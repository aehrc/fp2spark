Execute HIGH IMPACT / LOW RISK refactorings identified in the most recent code review.

## What to do:

1. **Locate recent code review:**
   - Check conversation history for recent code-reviewer agent output
   - If no recent review found, inform user they need to run `/review-branch` first

2. **Extract HIGH IMPACT / LOW RISK items:**
   - Parse the code review results
   - Identify all recommendations tagged as "HIGH IMPACT / LOW RISK"
   - Also consider "HIGH IMPACT / MEDIUM RISK" if there are few LOW RISK items

3. **Create implementation plan:**
   - List all refactorings to be performed
   - Order by dependencies (some refactorings may depend on others)
   - Estimate total effort

4. **Execute refactorings systematically:**

   For each refactoring:

   a. **Explain what you're about to do**
      - Describe the refactoring
      - Show which files will be affected

   b. **Implement the change**
      - Make code modifications
      - Update imports/references as needed

   c. **Verify compilation**
      - Run `mvn compile`
      - Fix any compilation errors

   d. **Run tests**
      - Run `mvn test`
      - Ensure all tests pass
      - If tests fail, fix or rollback

   e. **Mark as complete**
      - Update todo list
      - Move to next refactoring

5. **Commit all changes:**
   - Create comprehensive commit message with structure:
     ```
     refactor: <summary of all changes>

     Implements HIGH IMPACT/LOW RISK improvements from code review:

     1. [First refactoring with brief description]
     2. [Second refactoring with brief description]
     3. [etc.]

     **Changes:**
     - [Detailed change 1]
     - [Detailed change 2]

     **Testing:**
     - All [N] tests pass
     - Zero behavioral changes

     **Impact:** HIGH | **Risk:** LOW

     🤖 Generated with [Claude Code](https://claude.com/claude-code)

     Co-Authored-By: Claude <noreply@anthropic.com>
     ```

6. **Summary:**
   - Report all refactorings completed
   - Show test results
   - Display commit hash

## Important Rules:

- **NEVER skip testing** between refactorings
- **STOP immediately** if any test fails - debug or rollback
- **Ask user** if a refactoring seems ambiguous
- **Use git mv** for any file movements to preserve history
- **Keep refactorings atomic** - each should be a logical unit

## Usage:
- `/high-impact-refactorings` - Execute all HIGH IMPACT / LOW RISK items
- `/high-impact-refactorings --dry-run` - Show plan without executing
