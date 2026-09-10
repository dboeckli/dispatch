# AGENTS.md

`dispatch` ist ein Kafka-nachrichtengetriebener Spring-Boot-Service (`dev.lydtech.dispatch`,
Spring Boot 4.1.1, **Java 25**). Er konsumiert `order.created`, ruft den Stock-Service (lokal WireMock)
auf und publiziert Dispatch-Events. Die Messaging-DTOs/Events kommen aus
`dev.lydtech:dispatch-tracking-lib` (Maven, GitHub Packages
`maven.pkg.github.com/dboeckli/dispatch-tracking-lib`).

- App-Port `8082`, Kubernetes-NodePort `30082`, Namespace `dispatch`.
- Helm-Chart `dispatch-chart` mit lokalen Subcharts `dispatch-kafka-chart` (alias `kafka`) und
  `dispatch-wiremock-chart` (alias `wiremock`); Docker-Image-Push nach Docker Hub.
- Lokale Laufzeit: `compose.yaml` (Kafka, WireMock, `tracking-service`) via Spring Boot Docker Compose.

## Build & test commands

- Full build: `./mvnw clean install` — format checks (`validate`), unit tests, Helm lint/template/package.
- Schneller Build ohne App-Start/Docker/Helm-Push:
  `./mvnw clean install -Dskip.start.stop.springboot=true -Dskip.docker.build=true -Dskip.docker.publish=true`.
- Unit tests: `./mvnw test`. ITs: `./mvnw verify` (Failsafe). Einzelner Test: `./mvnw test -Dtest=DispatchServiceTest`.
- Format check only: `./mvnw validate` (spring-javaformat + spotless).

Nach Code-Änderungen immer verifizieren: das passende Maven-Ziel oben laufen lassen und die Ausgabe
als Evidenz melden (nicht nur „done").

## Sandbox build quirk (background)

Die Sandbox mountet das Repo per Filesystem-Passthrough (keine Symlinks) — Spotless' `npm install`
(prettier) bricht sonst mit `EPERM` ab. Das Kit setzt `npm_config_bin_links=false` global
(`spec.yaml` → `environment.variables`), daher ist hier **kein** manuelles `export` nötig. Auf dem
Host (Windows/CI) gilt das nicht.

## Formatting is enforced (fails the `validate` phase)

- Java: Spring Java Format → fix mit `./mvnw spring-javaformat:apply`.
- Alles andere (pom.xml, `**/*.md`, json, `application*.yaml`, `**/*.sh`): Spotless → fix mit
  `./mvnw spotless:apply`.
- `AGENTS.md` und `CLAUDE.md` sind bewusst von der Markdown-Formatierung ausgenommen
  (Spotless-Excludes); README.md und andere `.md`-Dateien müssen flexmark-clean bleiben.
- shfmt wird in `pom.xml` (Spotless) und den Workflows (`mfinelli/setup-shfmt`) synchron von Renovate
  gepflegt — Versionen nicht manuell auseinanderziehen.

## Deployment (Helm / Kubernetes)

```powershell
mvn clean install -DskipTests          # erzeugt target/helm/repo/dispatch-chart-<version>.tgz
# IntelliJ-Runner: deploy-k8s / test-k8s / uninstall-k8s (Namespace dispatch)
```

`./mvnw validate` prüft Helm nur teilweise — ein vollständiger Helm-Lint/Package-Lauf passiert in
`install` (im Sandbox ggf. mit `-Dskip.docker.build=true -Dskip.docker.publish=true`).
