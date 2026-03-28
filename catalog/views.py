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

from rest_framework.authentication import TokenAuthentication
from rest_framework.permissions import IsAuthenticated, IsAdminUser
from rest_framework.decorators import authentication_classes, permission_classes

from .models import Category, Product, ProductImage, Comment
from .serializers import CategorySerializer, ProductListSerializer, ProductDetailSerializer, CommentSerializer, StaffCommentSerializer


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


@api_view(['GET', 'POST'])
def comment_list(request, product_id):
    product = get_object_or_404(Product, pk=product_id)

    if request.method == 'GET':
        comments = product.comments.select_related('user').all()
        return Response(CommentSerializer(comments, many=True).data)

    # POST — требует авторизации
    auth = TokenAuthentication()
    try:
        user, _ = auth.authenticate(request)
    except Exception:
        return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)

    text = request.data.get('text', '').strip()
    if not text:
        return Response({'error': 'text is required'}, status=status.HTTP_400_BAD_REQUEST)

    comment = Comment.objects.create(product=product, user=user, text=text)
    return Response(CommentSerializer(comment).data, status=status.HTTP_201_CREATED)


# ── Staff comment endpoints ───────────────────────────────────────────────────

@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_comment_list(request):
    comments = (Comment.objects
                .select_related('user', 'product')
                .filter(product__is_hidden=False)
                .order_by('-created_at')[:50])
    return Response(StaffCommentSerializer(comments, many=True).data)


@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_comment_unread(request):
    count = Comment.objects.filter(product__is_hidden=False, is_read_by_staff=False).count()
    return Response({'unread': count})


@api_view(['POST'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_comment_mark_read(request, comment_id):
    comment = get_object_or_404(Comment, pk=comment_id)
    comment.is_read_by_staff = True
    comment.save(update_fields=['is_read_by_staff'])
    return Response({'ok': True})


@api_view(['DELETE'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_comment_delete(request, comment_id):
    comment = get_object_or_404(Comment, pk=comment_id)
    comment.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


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
    all_categories = Category.objects.all().order_by('name')
    product_category_ids = set(product.categories.values_list('id', flat=True))
    return render(request, 'mobile/product_detail.html', {
        'product': product,
        'images': images,
        'public_base': PUBLIC_BASE,
        'all_categories': all_categories,
        'product_category_ids': product_category_ids,
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
def mobile_product_toggle_category(request, pk, category_id):
    product = get_object_or_404(Product, pk=pk)
    category = get_object_or_404(Category, pk=category_id)
    if product.categories.filter(pk=category_id).exists():
        product.categories.remove(category)
        assigned = False
    else:
        product.categories.add(category)
        assigned = True
    return JsonResponse({'ok': True, 'assigned': assigned})


# ── Mobile2 views ─────────────────────────────────────────────────────────────

@staff_member_required
def mobile2_home(request):
    from django.db.models import Count
    categories = Category.objects.annotate(product_count=Count('products')).order_by('name')
    return render(request, 'mobile2/home.html', {
        'categories': categories,
        'public_base': PUBLIC_BASE,
    })


@staff_member_required
def mobile2_category_add(request):
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        cover_key = request.POST.get('cover_key', '').strip()
        if name:
            cat = Category.objects.create(name=name, cover_key=cover_key)
            return redirect('mobile2_category_detail', pk=cat.pk)
    return render(request, 'mobile2/category_add.html')


@staff_member_required
def mobile2_category_detail(request, pk):
    from django.db.models import Count
    category = get_object_or_404(Category, pk=pk)
    products = category.products.annotate(image_count=Count('images')).order_by('-created_at')
    return render(request, 'mobile2/category_detail.html', {
        'category': category,
        'products': products,
        'public_base': PUBLIC_BASE,
    })


@staff_member_required
def mobile2_product_add(request, category_id):
    category = get_object_or_404(Category, pk=category_id)
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        description = request.POST.get('description', '').strip()
        split = request.POST.get('split') == 'on'
        image_keys = json.loads(request.POST.get('image_keys', '[]'))

        if not name:
            return render(request, 'mobile2/product_add.html', {'category': category})

        if not image_keys:
            # Нет фото — создаём товар без обложки
            product = Product.objects.create(name=name, description=description)
            product.categories.add(category)
            return redirect('mobile2_product_detail', pk=product.pk)

        if split and len(image_keys) > 1:
            # Разбиваем на отдельные товары: обложка = фото = одно изображение
            for i, key in enumerate(image_keys, start=1):
                product = Product.objects.create(
                    name=f'{name} {i}',
                    description=description,
                    cover_key=key,
                )
                product.categories.add(category)
                ProductImage.objects.create(product=product, image_key=key)
            return redirect('mobile2_category_detail', pk=category_id)
        else:
            # Один товар: первая фото — обложка, все фото — галерея
            product = Product.objects.create(
                name=name,
                description=description,
                cover_key=image_keys[0],
            )
            product.categories.add(category)
            for key in image_keys:
                ProductImage.objects.create(product=product, image_key=key)
            return redirect('mobile2_product_detail', pk=product.pk)

    return render(request, 'mobile2/product_add.html', {'category': category})


@staff_member_required
def mobile2_product_detail(request, pk):
    product = get_object_or_404(Product, pk=pk)
    images = product.images.all().order_by('id')
    all_categories = Category.objects.all().order_by('name')
    product_category_ids = set(product.categories.values_list('id', flat=True))
    back_category = product.categories.order_by('id').first()
    return render(request, 'mobile2/product_detail.html', {
        'product': product,
        'images': images,
        'public_base': PUBLIC_BASE,
        'all_categories': all_categories,
        'product_category_ids': product_category_ids,
        'back_category': back_category,
    })


@staff_member_required
@require_http_methods(['POST'])
def mobile2_category_delete(request, pk):
    category = get_object_or_404(Category, pk=pk)
    category.delete()
    return redirect('mobile2_home')


@staff_member_required
@require_http_methods(['POST'])
def mobile2_product_delete(request, pk):
    product = get_object_or_404(Product, pk=pk)
    category_id = product.categories.values_list('id', flat=True).first()
    product.delete()
    if category_id:
        return redirect('mobile2_category_detail', pk=category_id)
    return redirect('mobile2_home')


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
