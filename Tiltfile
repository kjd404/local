# Read environment variables for cluster configuration, falling back to
# sensible defaults when they are not set.
CLUSTER_NAME = os.getenv("CLUSTER_NAME", "personal")
NAMESPACE = os.getenv("NAMESPACE", "personal")
REGISTRY_PORT = os.getenv("REGISTRY_PORT", "5001")
default_registry("k3d-%s-registry:%s" % (CLUSTER_NAME, REGISTRY_PORT))

def helm(name, chart, namespace='', values=[]):
    cmd = ['helm', 'template', name, chart]
    if namespace:
        cmd += ['--namespace', namespace]
    for v in values:
        cmd += ['-f', v]
    return local(cmd, echo_off=False)

helm_release = helm(
    name='platform',
    chart='charts/platform',
    namespace=NAMESPACE,
    values=['charts/platform/values.yaml', 'charts/platform/values.local.sops.yaml'],
)

docker_build('ingest-service', 'apps/ingest-service')
docker_build('teller-poller', 'apps/teller-poller')

k8s_yaml(helm_release)

k8s_resource('ingest-service', port_forwards=8080)
