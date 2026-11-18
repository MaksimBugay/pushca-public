#!/usr/bin/env bash
# Usage: central.sh status|publish <deploymentId>
# Talks to the Sonatype Central Portal API with the `central` server credentials from ~/.m2/settings.xml.
set -euo pipefail

action=${1:?status|publish}
id=${2:?deploymentId}

token=$(python3 -I - <<'EOF'
import xml.etree.ElementTree as ET, base64, os
root = ET.parse(os.path.expanduser('~/.m2/settings.xml')).getroot()
ns = root.tag[1:].split('}')[0] if root.tag.startswith('{') else ''
q = lambda t: '{%s}%s' % (ns, t) if ns else t
for s in root.iter(q('server')):
    if s.findtext(q('id')) == 'central':
        print(base64.b64encode((s.findtext(q('username')) + ':' + s.findtext(q('password'))).encode()).decode())
EOF
)

api=https://central.sonatype.com/api/v1/publisher
case "$action" in
  publish)
    curl -s -o /dev/null -w "publish HTTP %{http_code}\n" -X POST -H "Authorization: Bearer $token" "$api/deployment/$id"
    ;;
  status)
    curl -s -X POST -H "Authorization: Bearer $token" "$api/status?id=$id" |
      python3 -I -c 'import json,sys; d=json.load(sys.stdin); print(d["deploymentState"], d.get("errors") or "")'
    ;;
  *)
    echo "unknown action: $action" >&2; exit 2
    ;;
esac
