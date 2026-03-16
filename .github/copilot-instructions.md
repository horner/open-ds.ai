## OpenDS.ai Copilot Instructions

These instructions apply to the whole repository.

## Project Context

- OpenDS.ai is a Java 8 desktop application derived from OpenDS.
- The codebase has four main concerns: Swing display UI, headless CLI control, robot/network protocol handling, and the built-in MCP server.
- Keep the app lightweight. Avoid adding new dependencies unless the change clearly justifies the cost.
- Preserve existing behavior unless the request explicitly calls for a product change.

## Architecture Anchors

- `src/main/java/com/boomaa/opends/display/` contains the Swing entrypoint, frames, tabs, and UI-specific behavior.
- `src/main/java/com/boomaa/opends/headless/` contains the CLI/headless control path.
- `src/main/java/com/boomaa/opends/mcp/` contains the built-in MCP server and JSON-RPC helpers.
- `src/main/java/com/boomaa/opends/networking/` and `src/main/java/com/boomaa/opends/data/` contain protocol, transport, and robot state logic.
- `src/main/java/com/boomaa/opends/usb/` and the native build targets in `Makefile` are platform-sensitive; touch them only when necessary.

Prefer changes that stay within one layer. If logic is shared by display and headless flows, extract or place it in a neutral package instead of duplicating behavior.

## Change Philosophy

- Make the smallest viable change that fully solves the request.
- Prefer fixing the actual source of a bug over adding guard code around symptoms.
- Do not introduce broad refactors unless explicitly requested.
- Do not silently change protocol semantics, default ports, startup behavior, or command-line flags.
- Remove dead code when it is clearly obsolete, but do not do unrelated cleanup during a focused fix.

## Java Guidelines

- Maintain Java 8 compatibility. Do not use APIs or language features newer than Java 8.
- Follow the existing style in surrounding files rather than imposing a new style.
- Keep methods focused and names explicit.
- Avoid duplicating packet parsing, command dispatch, or state translation logic.
- Prefer straightforward control flow over abstraction-heavy designs.

## Swing And Threading

- Treat Swing code as EDT-sensitive. UI state changes should happen on the Event Dispatch Thread.
- Be careful with listeners, timers, and background network callbacks; do not block the UI thread.
- Preserve keyboard, controller, and robot-state interactions when modifying display code.
- If a fix affects both Swing and headless behavior, verify both execution paths still make sense.

## MCP And Protocol Guidance

- The MCP server is read-only. Do not add tool behavior that mutates robot state unless explicitly requested.
- Preserve MCP transport compatibility and existing tool names unless the request requires a breaking change.
- Keep default MCP behavior aligned with the docs: enabled by default, port `8765`, with support for `--disable-mcp` and `--mcp-port`.
- When touching startup or bind logic, make failure messages actionable and specific.

## Native And Platform-Specific Code

- Native USB changes should be tightly scoped and validated against the existing `Makefile` targets.
- Do not rename or reorganize native artifacts casually; packaging depends on the current resource layout.
- Favor portable Java changes over platform-specific branches when either would work.

## Validation

Use the same commands developers can run locally.

- Build/package: `mvn -B package --file pom.xml`
- Checkstyle: `mvn -B checkstyle:check --file pom.xml`
- Quick launch: `./start.sh`
- Common headless launch: `./start.sh --headless --disable-mcp --disable-nettables --disable-log`

There is currently no `src/test` tree. For most changes, validate with build/checkstyle plus a targeted run when behavior changes.

## Documentation Expectations

- Update `README.md` when setup, runtime behavior, or user-visible flags change.
- Update `docs/MCP.md` when MCP tools, transport behavior, ports, or client setup changes.
- Keep docs concrete and runnable; prefer exact commands over vague guidance.

## Pull Request Heuristics

- Keep changes easy to review.
- Prefer fewer touched files.
- If a change starts spanning multiple subsystems, stop and check whether the implementation can be simplified.
- Call out validation gaps explicitly when hardware-, OS-, or robot-specific behavior cannot be exercised locally.
