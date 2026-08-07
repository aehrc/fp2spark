Use the code-refactoring agent to perform architectural analysis on a file, class, or package.

## What to do:

1. **Identify target for analysis:**
   - If user provided path: Use that specific path
   - If user has file open in IDE: Use that file
   - If no context: Ask user what to analyze

2. **Detect scope:**
   - Single file: Analyze class responsibilities and dependencies
   - Package: Analyze package cohesion and organization
   - Multiple related files: Analyze subsystem architecture

3. **Invoke code-refactoring agent** with this prompt:
   ```
   Please perform architectural analysis on [target].

   Evaluate:

   1. **SOLID Principles:**
      - Single Responsibility: Does each class have one reason to change?
      - Open/Closed: Can we extend without modifying?
      - Liskov Substitution: Are abstractions properly designed?
      - Interface Segregation: Are interfaces focused?
      - Dependency Inversion: Do we depend on abstractions?

   2. **Package Organization:**
      - Is [target] in the correct package?
      - Does it mix domain concerns with application logic?
      - Are there layering violations?
      - Should anything be extracted or moved?

   3. **Separation of Concerns:**
      - Are responsibilities clearly separated?
      - Is there orchestration mixed with business logic?
      - Should a facade be introduced?

   4. **Code Quality:**
      - Coupling (dependencies on other classes/packages)
      - Cohesion (internal consistency)
      - Complexity (method/class size, cyclomatic complexity)

   Provide specific recommendations with:
   - What to refactor (concrete actions)
   - Why (architectural rationale)
   - Impact assessment (HIGH/MEDIUM/LOW)
   - Risk assessment (HIGH/MEDIUM/LOW)
   - Migration strategy if complex
   ```

4. **Present findings** with actionable recommendations

## Usage:
- `/analyze-architecture` - Analyzes currently open file
- `/analyze-architecture src/main/java/au/csiro/fhirpath/package` - Analyzes specific package
- `/analyze-architecture ClassName` - Analyzes specific class
