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
    namespace='personal',
    values=['charts/platform/values.yaml', 'charts/platform/values.local.sops.yaml'],
)

docker_build('ingest-service', 'apps/ingest-service')

k8s_yaml(helm_release)

k8s_resource('ingest-service', port_forwards=8080)
