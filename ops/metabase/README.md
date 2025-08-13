# Metabase Ops

Start Metabase without Helm using the official Docker image:

```bash
kubectl port-forward svc/platform-postgresql 5432:5432 -n personal &
docker run -d -p 8080:3000 --name metabase \
  -e MB_DB_TYPE=postgres \
  -e MB_DB_DBNAME=personal \
  -e MB_DB_PORT=5432 \
  -e MB_DB_USER=user \
  -e MB_DB_PASS=changeme \
  -e MB_DB_HOST=host.docker.internal \
  metabase/metabase
```

Then open <http://localhost:8080> to finish the Metabase setup.
