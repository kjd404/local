# Load variables from a local .env file so Tilt picks up settings like DB
# credentials without requiring a separate `source` step.
if os.path.exists('.env'):
    for line in str(read_file('.env')).splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            os.putenv(key, value)

# Read environment variables for cluster configuration, falling back to
# sensible defaults when they are not set.
CLUSTER_NAME = os.getenv("CLUSTER_NAME", "personal")
NAMESPACE = os.getenv("NAMESPACE", "personal")
REGISTRY_PORT = os.getenv("REGISTRY_PORT", "5001")
default_registry("k3d-%s-registry:%s" % (CLUSTER_NAME, REGISTRY_PORT))

def helm(name, chart, namespace=''):
    cmd = ['helm', 'template', name, chart]
    if namespace:
        cmd += ['--namespace', namespace]
    cmd += ['-f', 'charts/platform/values.yaml']
    local_values = 'charts/platform/values.local.yaml'
    if os.path.exists(local_values):
        cmd += ['-f', local_values]

    db_url = os.getenv('DB_URL')
    if db_url:
        cmd += ['--set', 'db.url=%s' % db_url]
    db_user = os.getenv('DB_USER')
    if db_user:
        cmd += ['--set', 'db.username=%s' % db_user]
    db_password = os.getenv('DB_PASSWORD')
    if db_password:
        cmd += ['--set', 'db.password=%s' % db_password]

    return local(cmd, echo_off=False)

helm_release = helm(
    name='platform',
    chart='charts/platform',
    namespace=NAMESPACE,
)

docker_build('ingest-service', 'apps/ingest-service')

k8s_yaml(helm_release)

k8s_resource('ingest-service', port_forwards=8080)
