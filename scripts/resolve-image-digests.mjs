// Read-only registry lookup. Review output before updating Dockerfile base images.
for (const [repository, tag] of [['library/maven', '3.9.9-eclipse-temurin-21'], ['library/eclipse-temurin', '21-jre-jammy']]) {
  const tokenResponse = await fetch(`https://auth.docker.io/token?service=registry.docker.io&scope=repository:${repository}:pull`);
  if (!tokenResponse.ok) throw new Error(`Registry authentication: ${tokenResponse.status}`);
  const { token } = await tokenResponse.json();
  const manifest = await fetch(`https://registry-1.docker.io/v2/${repository}/manifests/${tag}`, {
    method: 'HEAD', headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json' },
  });
  if (!manifest.ok) throw new Error(`Manifest lookup: ${manifest.status}`);
  console.log(`${repository.replace('library/', '')}:${tag}@${manifest.headers.get('docker-content-digest')}`);
}
