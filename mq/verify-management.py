"""Read-only HTTP assertions, except one intentionally denied queue declaration."""
import base64
import json
import os
from pathlib import Path
import urllib.error
import urllib.request


def main():
    creds_env = os.environ.get('EVENTOMAX_TEST_CREDENTIALS_PATH')
    creds_path = Path(creds_env) if creds_env else (Path(__file__).parent / '.local-secrets/credentials.json')
    credentials = json.loads(creds_path.read_text())
    host = os.environ.get('EVENTOMAX_TEST_HOST', '127.0.0.1')
    port = os.environ.get('EVENTOMAX_TEST_MGMT_PORT', '15672')
    base_url = f'http://{host}:{port}/api/'
    def request(role, path, method='GET', body=None):
        identity = credentials[role]
        token = base64.b64encode((identity['username'] + ':' + identity['password']).encode()).decode()
        req = urllib.request.Request(base_url + path, method=method,
            headers={'Authorization': 'Basic ' + token, 'Content-Type': 'application/json'},
            data=json.dumps(body).encode() if body is not None else None)
        try:
            with urllib.request.urlopen(req, timeout=10) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, None
    for path in ['whoami', 'exchanges/%2F', 'queues/%2F', 'bindings/%2F', 'overview']:
        status, data = request('management', path)
        assert status == 200, (path, status)
        if path == 'whoami':
            assert data['tags'] == ['management'], 'Unexpected management privileges'
        if path == 'queues/%2F':
            assert {'q.cmd.' + x + suffix for x in ('email', 'crew', 'quote')
                    for suffix in ('', '.dlq')} <= {x['name'] for x in data}
        print('PASS observer GET ' + path)
    for path in ['users', 'permissions']:
        status, _ = request('management', path)
        assert status in (401, 403), (path, status)
        print('PASS observer denied ' + path)
    status, _ = request('management', 'queues/%2F/demo.forbidden', 'PUT', {'durable': True, 'arguments': {}})
    assert status in (401, 403), status
    print('PASS observer cannot create queues')
    for role in ['application', 'rabbit_admin']:
        status, _ = request(role, 'overview')
        assert status in (401, 403), (role, status)
        print('PASS AMQP-only identity denied HTTP ' + role)


if __name__ == '__main__':
    main()
