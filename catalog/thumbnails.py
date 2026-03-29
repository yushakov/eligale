"""
Thumbnail generation for images stored in Yandex Object Storage.

Naming convention:
    original key:  catalog/abc123.jpg
    100px thumb:   catalog/abc123_100.jpg
    200px thumb:   catalog/abc123_200.jpg
    300px thumb:   catalog/abc123_300.jpg
    600px thumb:   catalog/abc123_600.jpg

Generated sizes cover all Android use cases:
    100 — ProductListScreen (4-column grid)
    200 — ProductGallery (3-column grid, kept for potential future use)
    300 — ProductGallery (3-column grid, current)
    600 — Category covers (circle, ~168dp × up to 4× density)
"""

import io
import logging
import threading

import boto3
from botocore.client import Config
from botocore.exceptions import ClientError
from django.conf import settings
from PIL import Image, ImageOps

logger = logging.getLogger(__name__)

THUMBNAIL_SIZES = [100, 200, 300, 600]


def _s3():
    return boto3.client(
        's3',
        endpoint_url=settings.YA_PUBLIC_UPLOADER_ENDPOINT_URL,
        region_name=settings.YA_PUBLIC_UPLOADER_REGION_NAME,
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        config=Config(signature_version='s3v4'),
    )


def thumbnail_key(key: str, size: int) -> str:
    """Return the S3 key for a thumbnail of the given size.

    'catalog/abc123.jpg' + 300  →  'catalog/abc123_300.jpg'
    'catalog/abc123'     + 300  →  'catalog/abc123_300'
    """
    dot = key.rfind('.')
    if dot == -1:
        return f'{key}_{size}'
    return f'{key[:dot]}_{size}{key[dot:]}'


def generate_thumbnails(key: str) -> None:
    """Download original from S3, generate all thumbnail sizes, upload back.

    Runs synchronously — call generate_thumbnails_async() for fire-and-forget.
    """
    if not key:
        return

    bucket = settings.YA_PUBLIC_UPLOADER_BUCKET_NAME
    s3 = _s3()

    try:
        obj = s3.get_object(Bucket=bucket, Key=key)
        data = obj['Body'].read()
    except ClientError:
        logger.exception('Could not download original for thumbnail generation: %s', key)
        return

    try:
        img = Image.open(io.BytesIO(data))
        img = ImageOps.exif_transpose(img)  # apply EXIF rotation before resizing
        img = img.convert('RGB')  # flatten alpha channel (PNG → JPEG)
    except Exception:
        logger.exception('Could not open image %s', key)
        return

    ext = key.rsplit('.', 1)[-1].lower() if '.' in key else 'jpg'
    pil_format = 'JPEG' if ext in ('jpg', 'jpeg') else ext.upper()
    content_type = 'image/jpeg' if pil_format == 'JPEG' else f'image/{ext}'

    for size in THUMBNAIL_SIZES:
        thumb = img.copy()
        thumb.thumbnail((size, size), Image.LANCZOS)
        buf = io.BytesIO()
        thumb.save(buf, format=pil_format, quality=85, optimize=True)
        buf.seek(0)
        try:
            s3.put_object(
                Bucket=bucket,
                Key=thumbnail_key(key, size),
                Body=buf,
                ContentType=content_type,
            )
        except ClientError:
            logger.exception('Could not upload %dpx thumbnail for %s', size, key)
            return

    logger.info('Thumbnails generated for %s', key)


def generate_thumbnails_async(key: str) -> None:
    """Fire-and-forget: start thumbnail generation in a daemon thread."""
    threading.Thread(target=generate_thumbnails, args=(key,), daemon=True).start()


def thumbnails_exist(key: str) -> bool:
    """Check whether thumbnails already exist for this key.

    Uses the largest size in THUMBNAIL_SIZES as indicator — if it's present,
    all smaller sizes were generated in the same run. This means adding a new
    larger size to THUMBNAIL_SIZES will automatically trigger regeneration on
    the next manage.py generate_missing_thumbnails run.
    """
    if not key:
        return True
    bucket = settings.YA_PUBLIC_UPLOADER_BUCKET_NAME
    s3 = _s3()
    largest = max(THUMBNAIL_SIZES)
    try:
        s3.head_object(Bucket=bucket, Key=thumbnail_key(key, largest))
        return True
    except ClientError as e:
        if e.response['Error']['Code'] in ('404', 'NoSuchKey'):
            return False
        raise
