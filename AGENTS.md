# Repository Guidelines

## Project Structure & Module Organization
`src/main/java/com/staryea/aiops` contains the Spring Boot backend. Keep code split by layer: `controller` for REST/SSE endpoints, `service` for business logic, `mapper` for MyBatis interfaces, `model` for DTO/entity classes, and `config`/`websocket` for infrastructure. MyBatis XML files live in `src/main/resources/mapper`, and SQL bootstrap files live in `src/main/resources/*.sql`. Packaging assets are in `bin/` and `src/main/assembly/assembly.xml`. The MCP sidecar is isolated in `mcp/server.py`; docs and static reference material live in `docs/`.

## Build, Test, and Development Commands
Use Maven from the repo root:

- `mvn clean package`: compiles the app, copies runtime dependencies, and creates `target/aiops-workbench-1.0.0-SNAPSHOT.jar` plus `*-dist.zip` and `*-dist.tar.gz`.
- `mvn test`: runs the Spring Boot test suite.
- `java -jar target/aiops-workbench-1.0.0-SNAPSHOT.jar --spring.config.location=file:src/main/resources/`: starts the backend locally.
- `bash bin/start.sh` / `bash bin/stop.sh` / `bash bin/restart.sh`: manage the packaged app with logs in `logs/console.log`.
- `python mcp/server.py`: runs the MCP bridge against `http://localhost:8089` by default.

## Coding Style & Naming Conventions
Follow the existing 4-space indentation in Java, XML, YAML, and Python. Keep Java packages under `com.staryea.aiops`. Use `PascalCase` for classes, `camelCase` for fields and methods, and kebab-case for REST paths such as `/api/workbench/todo-tasks`. When adding MyBatis queries, update both the mapper interface and the matching XML file with the same statement intent.

## Testing Guidelines
There is no committed `src/test` tree yet, but `spring-boot-starter-test` is already configured. Add new tests under `src/test/java` with names ending in `Test` for unit tests or `IT` for integration-style checks. Cover controller request handling, service branching, and mapper SQL changes. Run `mvn test` before opening a PR.

## Commit & Pull Request Guidelines
Current history uses short summaries (`Initial commit`, `bug修复`). Keep commits concise, imperative, and scoped to one change. PRs should describe the behavior change, list any config or schema impact, and include example requests/responses when APIs change. If you touch deployment scripts or packaging, mention the exact command used to verify the archive or startup flow.

## Security & Configuration Tips
`src/main/resources/application.yml` contains environment-backed defaults for MySQL, CoPaw, and Browser Hub. Prefer overriding values with environment variables such as `SPRING_DATASOURCE_URL` and `COPAW_BASE_URL`, and do not commit real credentials or new private network endpoints without review.
