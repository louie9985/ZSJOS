# Positioning confirmation public-link deployment

## Runtime contract

Positioning confirmation deliberately crosses three runtime boundaries:

1. An authorized operator calls `POST /admin-api/zsjos/positioning-card/{id}/student-link`.
2. The backend returns an absolute H5 page URL ending in `/positioning/share#token=...`.
3. The anonymous H5 page reads and submits the decision through
   `/public-api/zsjos/positioning-confirmation/detail` and
   `/public-api/zsjos/positioning-confirmation/decision`.

The token-generation command remains authenticated because it revokes earlier links and can advance
the positioning workflow. The H5 detail and decision endpoints are anonymous, with the opaque token
carried only in `X-Positioning-Token`. The URL fragment is not sent to the web server by the browser.

## Configuration ownership

The Spring property is declared in
`backend/yudao-server/src/main/resources/application.yaml`:

```yaml
zsjos:
  positioning:
    public-base-url: ${ZSJOS_PUBLIC_H5_BASE_URL:}
```

Local development overrides the empty default in `application-local.yaml`:

```yaml
zsjos:
  positioning:
    public-base-url: ${ZSJOS_PUBLIC_H5_BASE_URL:http://localhost:10086}
```

Production must set `ZSJOS_PUBLIC_H5_BASE_URL` to the externally reachable H5 origin or base path,
for example `https://h5.example.com` or `https://example.com/h5`. It must use `http` or `https`,
contain a host, and contain no credentials, query, or fragment. Do not set it to the employee
Workbench or Admin origin unless that reverse proxy explicitly serves the H5 `/positioning/share`
route there.

This repository currently has no backend application service in `deploy/production/compose.database.yml`
and no committed `application-prod.yaml`. That Compose file and `deploy/production/.env` own database
infrastructure only; adding this variable there has no effect unless a separate backend application
service consumes it.

Configure the value where the production backend process is actually defined:

- Docker Compose: add `ZSJOS_PUBLIC_H5_BASE_URL` under the backend application's `environment`, normally
  sourced from that deployment's environment file.
- Kubernetes or Helm: add the variable to the backend Deployment container environment or its reviewed
  values/ConfigMap source.
- systemd: add `Environment="ZSJOS_PUBLIC_H5_BASE_URL=https://h5.example.com"` in the backend service
  unit or a managed drop-in.
- Direct JAR or a release script: export the variable in the service account's runtime environment before
  starting the JAR. A command-line override
  `--zsjos.positioning.public-base-url=https://h5.example.com` is equivalent but should remain in the
  deployment configuration rather than source control.

The setting is read at backend startup. Changing it requires a controlled backend restart or rollout;
editing a frontend build alone does not change generated links.

## Reverse proxy requirements

The public H5 origin must:

- serve the H5 SPA for `/positioning/share` and return its entry document on direct navigation;
- proxy `/public-api/**` to the backend while preserving normal request headers;
- use HTTPS in production so the confirmation token is not exposed over plaintext transport;
- avoid redirecting `/positioning/share` to Workbench or Partner login.

The H5 page itself is anonymous. Other Partner H5 pages can retain their existing authentication rules.

## Release verification

1. Start or roll out the backend with `ZSJOS_PUBLIC_H5_BASE_URL` set.
2. Open the H5 `/positioning/share` route without a login session and confirm that the page shell loads.
3. Generate a fresh link as an authorized assigned operator.
4. Confirm that the returned origin is the configured H5 origin, never the Workbench origin.
5. Open the link in a logged-out mobile browser and verify detail loading through `/public-api`.
6. Verify both agree and request-changes behavior with controlled test data.
7. Regenerate once and confirm the earlier link is rejected while the latest link remains usable.

Never record complete confirmation tokens in deployment logs or verification reports. If a link was
shared through an unintended channel, regenerate it; regeneration revokes the previous active link.
