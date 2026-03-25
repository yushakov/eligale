import mimetypes
import os
import uuid
from datetime import datetime

import boto3
from botocore.client import Config
from django.conf import settings
from django.contrib.admin.views.decorators import staff_member_required
from django.http import JsonResponse
from django.utils.text import slugify
from django.views.decorators.csrf import csrf_protect
from django.views.decorators.http import require_http_methods


def _get_s3_client():
    session = boto3.session.Session()
    return session.client(
        service_name='s3',
        endpoint_url='https://storage.yandexcloud.net',
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        region_name=settings.YA_PUBLIC_UPLOADER_REGION_NAME,
        config=Config(signature_version='s3v4'),
    )


def _build_object_key(filename: str, prefix: str = 'catalog') -> str:
    base, ext = os.path.splitext(filename)
    safe_base = slugify(base) or 'file'
    date_path = datetime.utcnow().strftime('%Y-%m-%d')
    unique_id = uuid.uuid4().hex
    return f'{prefix}/{date_path}/{safe_base}-{unique_id}{ext.lower()}'


@staff_member_required
@csrf_protect
@require_http_methods(['POST'])
def presign_upload(request):
    filename = request.POST.get('filename')
    content_type = request.POST.get('content_type')

    if not filename:
        return JsonResponse({'error': 'filename is required'}, status=400)

    if not content_type:
        content_type = mimetypes.guess_type(filename)[0] or 'application/octet-stream'

    object_key = _build_object_key(filename)
    s3 = _get_s3_client()

    upload_url = s3.generate_presigned_url(
        ClientMethod='put_object',
        Params={
            'Bucket': settings.YA_PUBLIC_UPLOADER_BUCKET_NAME,
            'Key': object_key,
            'ContentType': content_type,
        },
        ExpiresIn=3600,
        HttpMethod='PUT',
    )

    public_url = f"{settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')}/{object_key}"

    return JsonResponse({
        'ok': True,
        'upload_url': upload_url,
        'object_key': object_key,
        'public_url': public_url,
        'content_type': content_type,
    })
