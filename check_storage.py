#!/usr/bin/env python
"""
Интеграционная проверка Yandex Object Storage.

Запуск:
    .venv/bin/python check_storage.py

Что делает:
  1. Загружает маленький тестовый файл в публичный бакет через put_object.
  2. Проверяет, что файл доступен по публичному URL (HTTP GET).
  3. Удаляет тестовый файл из публичного бакета.
  4. Генерирует presigned PUT URL для приватного бакета, загружает файл.
  5. Генерирует presigned GET URL, проверяет доступность.
  6. Удаляет тестовый файл из приватного бакета.
"""

import os
import sys
import uuid
import urllib.request
import urllib.error
from datetime import datetime

from dotenv import load_dotenv
import boto3
from botocore.client import Config

load_dotenv()

# ── Конфиг ────────────────────────────────────────────────────────────────────

PUBLIC_ACCESS_KEY   = os.environ['YA_PUBLIC_UPLOADER_ACCESS_KEY_ID']
PUBLIC_SECRET_KEY   = os.environ['YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY']
PUBLIC_REGION       = os.environ['YA_PUBLIC_UPLOADER_REGION_NAME']
PUBLIC_BUCKET       = os.environ['YA_PUBLIC_UPLOADER_BUCKET_NAME']
PUBLIC_BASE_URL     = os.environ['YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL'].rstrip('/')

PRIVATE_ACCESS_KEY  = os.environ['YA_PRIVATE_UPLOADER_ACCESS_KEY_ID']
PRIVATE_SECRET_KEY  = os.environ['YA_PRIVATE_UPLOADER_SECRET_ACCESS_KEY']
PRIVATE_REGION      = os.environ['YA_PRIVATE_UPLOADER_REGION_NAME']
PRIVATE_BUCKET      = os.environ['YA_PRIVATE_UPLOADER_BUCKET_NAME']

ENDPOINT = 'https://storage.yandexcloud.net'
TEST_CONTENT = b'eliza-storage-check ' + uuid.uuid4().hex.encode()
TEST_CONTENT_TYPE = 'text/plain'

# ── Helpers ───────────────────────────────────────────────────────────────────

OK   = '\033[92m✓\033[0m'
FAIL = '\033[91m✗\033[0m'
INFO = '\033[94m·\033[0m'


def make_key():
    date_path = datetime.utcnow().strftime('%Y-%m-%d')
    return f'storage-check/{date_path}/test-{uuid.uuid4().hex[:8]}.txt'


def s3_client(access_key, secret_key, region):
    return boto3.session.Session().client(
        service_name='s3',
        endpoint_url=ENDPOINT,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name=region,
        config=Config(signature_version='s3v4'),
    )


def http_get(url, expected_body=None):
    """GET запрос, возвращает (status_code, body)."""
    try:
        with urllib.request.urlopen(url, timeout=10) as resp:
            body = resp.read()
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, b''
    except Exception as e:
        return None, str(e).encode()


def http_put(url, data, content_type):
    """PUT запрос через urllib."""
    req = urllib.request.Request(url, data=data, method='PUT')
    req.add_header('Content-Type', content_type)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code


def step(label, ok, detail=''):
    icon = OK if ok else FAIL
    line = f'  {icon}  {label}'
    if detail:
        line += f'  ({detail})'
    print(line)
    return ok


# ── Публичный бакет ───────────────────────────────────────────────────────────

def check_public():
    print('\nПубличный бакет:', PUBLIC_BUCKET)
    s3 = s3_client(PUBLIC_ACCESS_KEY, PUBLIC_SECRET_KEY, PUBLIC_REGION)
    key = make_key()
    passed = True

    # 1. put_object
    try:
        s3.put_object(
            Bucket=PUBLIC_BUCKET,
            Key=key,
            Body=TEST_CONTENT,
            ContentType=TEST_CONTENT_TYPE,
        )
        passed &= step('put_object', True, key)
    except Exception as e:
        step('put_object', False, str(e))
        return False

    # 2. Доступность по публичному URL
    public_url = f'{PUBLIC_BASE_URL}/{key}'
    status, body = http_get(public_url)
    ok = status == 200 and body == TEST_CONTENT
    passed &= step(
        'публичный GET',
        ok,
        f'HTTP {status}, тело {"совпадает" if body == TEST_CONTENT else "НЕ совпадает"}',
    )
    if not ok:
        print(f'     URL: {public_url}')

    # 3. Presigned PUT → public URL
    key2 = make_key()
    try:
        presigned_put = s3.generate_presigned_url(
            ClientMethod='put_object',
            Params={'Bucket': PUBLIC_BUCKET, 'Key': key2, 'ContentType': TEST_CONTENT_TYPE},
            ExpiresIn=60,
            HttpMethod='PUT',
        )
        put_status = http_put(presigned_put, TEST_CONTENT, TEST_CONTENT_TYPE)
        passed &= step('presigned PUT', put_status in (200, 201, 204), f'HTTP {put_status}')
    except Exception as e:
        step('presigned PUT', False, str(e))
        key2 = None
        passed = False

    # 4. Удаление
    try:
        s3.delete_object(Bucket=PUBLIC_BUCKET, Key=key)
        if key2:
            s3.delete_object(Bucket=PUBLIC_BUCKET, Key=key2)
        step('delete_object', True)
    except Exception as e:
        step('delete_object', False, str(e))

    return passed


# ── Приватный бакет ───────────────────────────────────────────────────────────

def check_private():
    print('\nПриватный бакет (KMS):', PRIVATE_BUCKET)
    s3 = s3_client(PRIVATE_ACCESS_KEY, PRIVATE_SECRET_KEY, PRIVATE_REGION)
    key = make_key()
    passed = True

    # 1. Presigned PUT
    try:
        presigned_put = s3.generate_presigned_url(
            ClientMethod='put_object',
            Params={'Bucket': PRIVATE_BUCKET, 'Key': key, 'ContentType': TEST_CONTENT_TYPE},
            ExpiresIn=60,
            HttpMethod='PUT',
        )
        passed &= step('generate presigned PUT URL', True)
    except Exception as e:
        step('generate presigned PUT URL', False, str(e))
        return False

    put_status = http_put(presigned_put, TEST_CONTENT, TEST_CONTENT_TYPE)
    passed &= step('presigned PUT upload', put_status in (200, 201, 204), f'HTTP {put_status}')
    if put_status not in (200, 201, 204):
        return False

    # 2. Presigned GET
    try:
        presigned_get = s3.generate_presigned_url(
            ClientMethod='get_object',
            Params={'Bucket': PRIVATE_BUCKET, 'Key': key},
            ExpiresIn=60,
        )
        passed &= step('generate presigned GET URL', True)
    except Exception as e:
        step('generate presigned GET URL', False, str(e))
        return False

    status, body = http_get(presigned_get)
    ok = status == 200 and body == TEST_CONTENT
    passed &= step(
        'presigned GET download',
        ok,
        f'HTTP {status}, тело {"совпадает" if body == TEST_CONTENT else "НЕ совпадает"}',
    )

    # 3. Файл НЕ доступен напрямую (без подписи)
    direct_url = f'{ENDPOINT}/{PRIVATE_BUCKET}/{key}'
    direct_status, _ = http_get(direct_url)
    no_public_access = direct_status in (403, 401)
    passed &= step(
        'прямой доступ запрещён',
        no_public_access,
        f'HTTP {direct_status} {"— OK" if no_public_access else "— ОЖИДАЛСЯ 403!"}',
    )

    # 4. Удаление
    try:
        s3.delete_object(Bucket=PRIVATE_BUCKET, Key=key)
        step('delete_object', True)
    except Exception as e:
        step('delete_object', False, str(e))

    return passed


# ── Main ──────────────────────────────────────────────────────────────────────

if __name__ == '__main__':
    print('=' * 55)
    print('  Eliza Gallery — проверка хранилищ')
    print('=' * 55)

    results = []
    try:
        results.append(('Публичный бакет', check_public()))
    except KeyError as e:
        print(f'  {FAIL}  Не хватает переменной окружения: {e}')
        results.append(('Публичный бакет', False))

    try:
        results.append(('Приватный бакет', check_private()))
    except KeyError as e:
        print(f'  {FAIL}  Не хватает переменной окружения: {e}')
        results.append(('Приватный бакет', False))

    print('\n' + '=' * 55)
    all_ok = True
    for name, ok in results:
        icon = OK if ok else FAIL
        print(f'  {icon}  {name}')
        all_ok = all_ok and ok

    print('=' * 55)
    sys.exit(0 if all_ok else 1)
