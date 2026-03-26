#!/usr/bin/env python
"""
Загружает landing-img в публичный бакет.

Запуск:
    .venv/bin/python upload_landing_images.py

Кладёт файлы под префикс landing/ и выводит итоговые URL.
"""
import os
import sys
import mimetypes
from pathlib import Path

from dotenv import load_dotenv
import boto3
from botocore.client import Config

load_dotenv()

BUCKET      = os.environ['YA_PUBLIC_UPLOADER_BUCKET_NAME']
ACCESS_KEY  = os.environ['YA_PUBLIC_UPLOADER_ACCESS_KEY_ID']
SECRET_KEY  = os.environ['YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY']
REGION      = os.environ['YA_PUBLIC_UPLOADER_REGION_NAME']
PUBLIC_BASE = os.environ['YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL'].rstrip('/')
ENDPOINT    = 'https://storage.yandexcloud.net'

SRC_DIR = Path(__file__).parent / 'static_src' / 'landing-img'

OK   = '\033[92m✓\033[0m'
FAIL = '\033[91m✗\033[0m'


def main():
    s3 = boto3.session.Session().client(
        service_name='s3',
        endpoint_url=ENDPOINT,
        aws_access_key_id=ACCESS_KEY,
        aws_secret_access_key=SECRET_KEY,
        region_name=REGION,
        config=Config(signature_version='s3v4'),
    )

    files = sorted([
        p for p in SRC_DIR.rglob('*')
        if p.is_file() and not p.name.startswith('.')
    ])

    print(f'Загружаю {len(files)} файлов в бакет {BUCKET}/landing/\n')

    urls = {}
    errors = 0

    for path in files:
        # Относительный путь внутри landing-img → ключ в бакете
        rel = path.relative_to(SRC_DIR)
        key = f'landing/{rel.as_posix()}'
        content_type = mimetypes.guess_type(path.name)[0] or 'application/octet-stream'

        try:
            with open(path, 'rb') as f:
                s3.put_object(
                    Bucket=BUCKET,
                    Key=key,
                    Body=f,
                    ContentType=content_type,
                )
            url = f'{PUBLIC_BASE}/{key}'
            urls[str(rel)] = url
            print(f'  {OK}  {key}')
        except Exception as e:
            print(f'  {FAIL}  {key}  — {e}')
            errors += 1

    print(f'\n{"=" * 60}')
    if errors:
        print(f'Ошибок: {errors}')
    else:
        print('Все файлы загружены.\n')
        print('URL для index.html:')
        print(f'  Логотип:  {urls.get("logo-central.png", "—")}')
        print(f'  Тайлы:')
        for name, url in sorted(urls.items()):
            if name.startswith('tile/'):
                print(f'    {url}')

    sys.exit(bool(errors))


if __name__ == '__main__':
    main()
