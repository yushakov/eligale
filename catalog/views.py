import json
import mimetypes
import os
import uuid
from datetime import datetime

import boto3
from botocore.client import Config
from django.conf import settings
from django.contrib.admin.views.decorators import staff_member_required
from django.http import JsonResponse
from django.shortcuts import get_object_or_404, redirect, render
from django.utils.text import slugify
from django.views.decorators.csrf import csrf_protect
from django.views.decorators.http import require_http_methods
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import Category, Product, ProductImage
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
    categories = Category.objects.filter(is_hidden=False)
    return Response(CategorySerializer(categories, many=True).data)


@api_view(['GET'])
def product_list(request, category_id):
    try:
        category = Category.objects.get(pk=category_id)
    except Category.DoesNotExist:
        return Response({'error': 'Not found'}, status=status.HTTP_404_NOT_FOUND)
    products = category.products.filter(is_hidden=False).order_by('-created_at')
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


# ── Mobile views ──────────────────────────────────────────────────────────────

PUBLIC_BASE = settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')


@staff_member_required
def mobile_home(request):
    categories = Category.objects.all().order_by('name')
    return render(request, 'mobile/home.html', {
        'categories': categories,
        'public_base': PUBLIC_BASE,
    })


@staff_member_required
def mobile_category_add(request):
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        cover_key = request.POST.get('cover_key', '').strip()
        if name:
            cat = Category.objects.create(name=name, cover_key=cover_key)
            return redirect('mobile_category_detail', pk=cat.pk)
    return render(request, 'mobile/category_add.html')


@staff_member_required
def mobile_category_detail(request, pk):
    category = get_object_or_404(Category, pk=pk)
    products = category.products.all().order_by('-created_at')
    return render(request, 'mobile/category_detail.html', {
        'category': category,
        'products': products,
        'public_base': PUBLIC_BASE,
    })


@staff_member_required
def mobile_product_add(request, category_id):
    category = get_object_or_404(Category, pk=category_id)
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        description = request.POST.get('description', '').strip()
        cover_key = request.POST.get('cover_key', '').strip()
        if name:
            product = Product.objects.create(
                name=name, description=description, cover_key=cover_key
            )
            product.categories.add(category)
            return redirect('mobile_product_detail', pk=product.pk)
    return render(request, 'mobile/product_add.html', {'category': category})


@staff_member_required
def mobile_product_detail(request, pk):
    product = get_object_or_404(Product, pk=pk)
    images = product.images.all().order_by('id')
    return render(request, 'mobile/product_detail.html', {
        'product': product,
        'images': images,
        'public_base': PUBLIC_BASE,
    })


@staff_member_required
@require_http_methods(['POST'])
def mobile_product_image_add(request, pk):
    product = get_object_or_404(Product, pk=pk)
    try:
        data = json.loads(request.body)
        image_key = data.get('image_key', '').strip()
    except (json.JSONDecodeError, AttributeError):
        return JsonResponse({'error': 'Invalid JSON'}, status=400)
    if not image_key:
        return JsonResponse({'error': 'image_key required'}, status=400)
    ProductImage.objects.create(product=product, image_key=image_key)
    return JsonResponse({'ok': True})


@staff_member_required
@require_http_methods(['POST'])
def mobile_category_toggle_hidden(request, pk):
    category = get_object_or_404(Category, pk=pk)
    category.is_hidden = not category.is_hidden
    category.save(update_fields=['is_hidden'])
    return JsonResponse({'ok': True, 'is_hidden': category.is_hidden})


@staff_member_required
@require_http_methods(['POST'])
def mobile_product_toggle_hidden(request, pk):
    product = get_object_or_404(Product, pk=pk)
    product.is_hidden = not product.is_hidden
    product.save(update_fields=['is_hidden'])
    return JsonResponse({'ok': True, 'is_hidden': product.is_hidden})
