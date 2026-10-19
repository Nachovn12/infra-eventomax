"""Generate private local identities once. Never print passwords or replace existing files."""
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parent
PRIVATE = Path(os.environ['EVENTOMAX_TEST_SECRETS_DIR']) if os.environ.get('EVENTOMAX_TEST_SECRETS_DIR') else (ROOT / '.local-secrets')
OPERATIONAL = r'^(cmd\.(direct|topic|dead\.dlx)|q\.cmd\.(email|crew|quote)(\.dlq)?)$'
DEMO = r'^demo\.[A-Za-z0-9][A-Za-z0-9._-]{0,122}$'


def password_hash(password):
    salt = secrets.token_bytes(4)
    return base64.b64encode(salt + hashlib.sha256(salt + password.encode()).digest()).decode()


def prepare():
    # A partial directory requires investigation, never silent regeneration/rotation.
    if PRIVATE.exists():
        raise SystemExit('Private directory already exists; reuse it. No files changed.')
    PRIVATE.mkdir(mode=0o700)
    if os.name == 'nt':
        who = subprocess.run(['whoami', '/user', '/fo', 'csv', '/nh'],
                             check=True, capture_output=True, text=True).stdout
        import csv
        sid = next(csv.reader([who.strip()]))[1]
        subprocess.run(['icacls', str(PRIVATE), '/inheritance:r', '/grant:r',
                        f'*{sid}:(OI)(CI)F', '*S-1-5-18:(OI)(CI)F'],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    credentials = {}
    definitions = {'vhosts': [{'name': '/'}], 'users': [], 'permissions': []}
    for role, username, tags, permission in [
        ('application', 'eventomax-app', [], OPERATIONAL),
        ('rabbit_admin', 'eventomax-rabbit-admin', [], DEMO),
        ('management', 'EventoMax', ['management'], '^$'),
    ]:
        password = secrets.token_urlsafe(32)
        credentials[role] = {'username': username, 'password': password}
        definitions['users'].append({'name': username, 'password_hash': password_hash(password),
                                     'hashing_algorithm': 'rabbit_password_hashing_sha256', 'tags': tags})
        definitions['permissions'].append({'user': username, 'vhost': '/',
                                           'configure': permission, 'write': permission, 'read': permission})
    for name, value in [('credentials.json', credentials), ('identities.json', definitions)]:
        fd = os.open(PRIVATE / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as stream:
            json.dump(value, stream, indent=2)
            stream.write('\n')
    print('Private local files created. Passwords are not printed. See mq/LOCAL_BOOTSTRAP.md.')


if __name__ == '__main__':
    prepare()
