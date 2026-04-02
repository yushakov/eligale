"""
Backup SQLite database to Yandex Object Storage (private bucket).

Usage:
    python manage.py backup_db

Keeps the last 7 days of backups; older files are deleted automatically.
"""

import gzip
import os
import shutil
import tempfile
from datetime import datetime, timedelta, timezone

import boto3
from botocore.client import Config
from django.conf import settings
from django.core.management.base import BaseCommand

BACKUP_PREFIX = 'db-backup/'
KEEP_DAYS = 7


class Command(BaseCommand):
    help = 'Backup db.sqlite3 to the private Yandex Object Storage bucket'

    def handle(self, *args, **options):
        db_path = settings.BASE_DIR / 'db.sqlite3'
        if not db_path.exists():
            self.stderr.write(self.style.ERROR(f'Database not found: {db_path}'))
            return

        timestamp = datetime.now(timezone.utc).strftime('%Y-%m-%d_%H-%M-%S')
        backup_key = f'{BACKUP_PREFIX}db_{timestamp}.sqlite3.gz'

        tmp_path = None
        try:
            # Gzip a copy of the DB into a temp file
            with tempfile.NamedTemporaryFile(suffix='.gz', delete=False) as tmp:
                tmp_path = tmp.name
            with open(db_path, 'rb') as f_in, gzip.open(tmp_path, 'wb') as f_out:
                shutil.copyfileobj(f_in, f_out)

            s3 = _s3_client()
            bucket = settings.YA_PRIVATE_UPLOADER_BUCKET_NAME

            s3.upload_file(tmp_path, bucket, backup_key)
            size_mb = os.path.getsize(tmp_path) / 1024 / 1024
            self.stdout.write(self.style.SUCCESS(
                f'Backup uploaded: {backup_key} ({size_mb:.2f} MB)'
            ))

            _delete_old_backups(s3, bucket, self.stdout)

        except Exception as e:
            self.stderr.write(self.style.ERROR(f'Backup failed: {e}'))
            raise
        finally:
            if tmp_path and os.path.exists(tmp_path):
                os.unlink(tmp_path)


def _s3_client():
    return boto3.client(
        's3',
        endpoint_url='https://storage.yandexcloud.net',
        region_name=settings.YA_PRIVATE_UPLOADER_REGION_NAME,
        aws_access_key_id=settings.YA_PRIVATE_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PRIVATE_UPLOADER_SECRET_ACCESS_KEY,
        config=Config(signature_version='s3v4'),
    )


def _delete_old_backups(s3, bucket, stdout):
    cutoff = datetime.now(timezone.utc) - timedelta(days=KEEP_DAYS)
    paginator = s3.get_paginator('list_objects_v2')
    for page in paginator.paginate(Bucket=bucket, Prefix=BACKUP_PREFIX):
        for obj in page.get('Contents', []):
            if obj['LastModified'] < cutoff:
                s3.delete_object(Bucket=bucket, Key=obj['Key'])
                stdout.write(f'Deleted old backup: {obj["Key"]}')
