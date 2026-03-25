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
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import Category, Product
from .serializers import CategorySerializer, ProductListSerializer, ProductDetailSerializer


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


@api_view(['GET'])
def category_list(request):
    categories = Category.objects.all()
    return Response(CategorySerializer(categories, many=True).data)


@api_view(['GET'])
def product_list(request, category_id):
    try:
        category = Category.objects.get(pk=category_id)
    except Category.DoesNotExist:
        return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)
    products = category.products.all().order_by('-created_at')
    return Response(ProductListSerializer(products, many=True).data)


@api_view(['GET'])
def product_detail(request, product_id):
    try:
        product = Product.objects.prefetch_related('images').get(pk=product_id)
    except Product.DoesNotExist:
        return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)
    return Response(ProductDetailSerializer(product).data)


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
