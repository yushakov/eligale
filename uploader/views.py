import mimetypes
import os
import uuid
from datetime import datetime

import boto3
from botocore.client import Config
from django.conf import settings
from django.http import JsonResponse
from django.shortcuts import render
from django.utils.text import slugify
from django.views.decorators.csrf import csrf_protect
from django.views.decorators.http import require_http_methods
import traceback

import logging
logging.basicConfig(level=logging.DEBUG)
logging.getLogger("botocore").setLevel(logging.DEBUG)
logging.getLogger("boto3").setLevel(logging.DEBUG)

"""
def get_s3_client():
    return boto3.client(
        "s3",
        endpoint_url=settings.YA_PUBLIC_UPLOADER_ENDPOINT_URL,
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        region_name=settings.YA_PUBLIC_UPLOADER_REGION_NAME,
        config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
    )

def get_s3_client():
    session = boto3.session.Session()
    return session.client(
        service_name="s3",
        endpoint_url="https://storage.yandexcloud.net",
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        #region_name="ru-central1",
        region_name="us-east-1",
    )
"""

def get_s3_client():
    session = boto3.session.Session()
    return session.client(
        service_name="s3",
        endpoint_url="https://storage.yandexcloud.net",
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        region_name="ru-central1",
        config=Config(
            signature_version="s3v4",
            retries={"max_attempts": 2, "mode": "standard"},
        ),
    )

def build_object_key(filename: str) -> str:
    base, ext = os.path.splitext(filename)
    safe_base = slugify(base) or "file"
    date_path = datetime.utcnow().strftime("%Y-%m-%d")
    unique_id = uuid.uuid4().hex
    return f"test-uploads/{date_path}/{safe_base}-{unique_id}{ext.lower()}"


@require_http_methods(["GET"])
def test_image_upload_page(request):
    return render(request, "test_image_upload.html")


@csrf_protect
@require_http_methods(["POST"])
def test_image_upload_api(request):
    print("here, in test_image_upload_api")
    uploaded_file = request.FILES.get("file")
    if not uploaded_file:
        return JsonResponse({"error": "No file uploaded"}, status=400)

    try:
        object_key = build_object_key(uploaded_file.name)
        content_type = uploaded_file.content_type or "application/octet-stream"
        body = uploaded_file.read()

        s3 = get_s3_client()

        # сперва простой тест доступа
        s3.list_objects_v2(
            Bucket=settings.YA_PUBLIC_UPLOADER_BUCKET_NAME,
            MaxKeys=1,
        )

        resp = s3.put_object(
            Bucket=settings.YA_PUBLIC_UPLOADER_BUCKET_NAME,
            Key=object_key,
            Body=body,
            ContentType=content_type,
        )

        public_url = f"{settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')}/{object_key}"

        return JsonResponse({
            "ok": True,
            "etag": resp.get("ETag"),
            "url": public_url,
            "object_key": object_key,
            "content_type": content_type,
            "size": len(body),
        })
    except Exception as e:
        return JsonResponse({
            "error": "Upload failed",
            "details": repr(e),
            "trace": traceback.format_exc(),
        }, status=500)
