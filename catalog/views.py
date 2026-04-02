import json
import mimetypes
import os
import random
import string
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

from .models import AppRelease, Category, Product, ProductImage, Comment, FavoriteImage, CommentReport
from .serializers import CategorySerializer, ProductListSerializer, ProductDetailSerializer, CommentSerializer, StaffCommentSerializer, FavoriteImageSerializer


def _make_snippet(text: str, query: str, context: int = 80) -> str:
    idx = text.lower().find(query.lower())
    if idx == -1:
        end = min(len(text), 160)
        return text[:end] + ('…' if len(text) > end else '')
    start = max(0, idx - context)
    end = min(len(text), idx + len(query) + context)
    snippet = text[start:end]
    if start > 0:
        snippet = '…' + snippet
    if end < len(text):
        snippet = snippet + '…'
    return snippet


@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def my_comments(request):
    comments = (
        Comment.objects
        .filter(user=request.user)
        .select_related('product')
        .order_by('-created_at')
    )
    data = [
        {
            'id': c.id,
            'product_id': c.product_id,
            'product_name': c.product.name,
            'text': c.text,
            'created_at': c.created_at.isoformat(),
        }
        for c in comments
    ]
    return Response(data)


@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def search(request):
    q = request.GET.get('q', '').strip()
    if not q:
        return Response([])

    from chat.models import ChatMessage as CM

    results = []

    comments_qs = (
        Comment.objects
        .filter(text__icontains=q)
        .select_related('product', 'user')
        .order_by('-created_at')[:100]
    )
    for c in comments_qs:
        results.append({
            'type': 'comment',
            'id': c.id,
            'snippet': _make_snippet(c.text, q),
            'created_at': c.created_at.isoformat(),
            'product_id': c.product_id,
            'product_name': c.product.name,
            'author': c.user.display_name or c.user.email,
        })

    if request.user.is_staff:
        messages_qs = CM.objects.filter(text__icontains=q).select_related('chat', 'chat__user')
    else:
        messages_qs = CM.objects.filter(chat__user=request.user, text__icontains=q).select_related('chat', 'chat__user')

    for m in messages_qs.order_by('-created_at')[:100]:
        results.append({
            'type': 'message',
            'id': m.id,
            'snippet': _make_snippet(m.text, q),
            'created_at': m.created_at.isoformat(),
            'chat_user_id': m.chat.user_id,
            'user_email': m.chat.user.email,
        })

    results.sort(key=lambda x: x['created_at'], reverse=True)
    return Response(results[:100])


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


def _delete_s3_image_and_thumbnails(key: str) -> None:
    """Delete original + all thumbnail variants from the public bucket."""
    if not key:
        return
    from .thumbnails import THUMBNAIL_SIZES, thumbnail_key as _thumb_key
    s3 = _get_s3_client()
    bucket = settings.YA_PUBLIC_UPLOADER_BUCKET_NAME
    keys_to_delete = [key] + [_thumb_key(key, size) for size in THUMBNAIL_SIZES]
    objects = [{'Key': k} for k in keys_to_delete]
    try:
        s3.delete_objects(Bucket=bucket, Delete={'Objects': objects, 'Quiet': True})
    except Exception:
        pass


def _random_product_name() -> str:
    return ''.join(random.choices(string.ascii_lowercase + string.digits, k=8))


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

    allowed_prefixes = {'catalog', 'chat', 'apk'}
    prefix = request.POST.get('prefix', 'catalog')
    if prefix not in allowed_prefixes:
        prefix = 'catalog'
    object_key = _build_object_key(filename, prefix=prefix)
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


# ── Comment report endpoints ─────────────────────────────────────────────────

@api_view(['POST'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def report_comment(request, comment_id):
    if request.user.is_staff:
        return Response({'error': 'Staff cannot report comments'}, status=status.HTTP_403_FORBIDDEN)
    comment = get_object_or_404(Comment, pk=comment_id)
    if comment.user == request.user:
        return Response({'error': 'Cannot report own comment'}, status=status.HTTP_400_BAD_REQUEST)
    text = request.data.get('text', '').strip()[:150]
    if not text:
        return Response({'error': 'text is required'}, status=status.HTTP_400_BAD_REQUEST)
    _, created = CommentReport.objects.get_or_create(
        comment=comment, reporter=request.user,
        defaults={'text': text}
    )
    if not created:
        return Response({'error': 'Already reported'}, status=status.HTTP_400_BAD_REQUEST)
    return Response({'ok': True}, status=status.HTTP_201_CREATED)


@api_view(['DELETE'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def delete_own_comment(request, comment_id):
    comment = get_object_or_404(Comment, pk=comment_id)
    if comment.user != request.user:
        return Response({'error': 'Forbidden'}, status=status.HTTP_403_FORBIDDEN)
    comment.delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_report_list(request):
    reports = (CommentReport.objects
               .select_related('comment', 'comment__user', 'comment__product', 'reporter')
               .order_by('-created_at'))
    data = [
        {
            'id': r.id,
            'comment_id': r.comment.id,
            'comment_text': r.comment.text,
            'comment_author': r.comment.user.display_name or r.comment.user.email,
            'reporter_email': r.reporter.email,
            'text': r.text,
            'is_read': r.is_read,
            'created_at': r.created_at.isoformat(),
        }
        for r in reports
    ]
    # Помечаем как прочитанные после формирования ответа
    CommentReport.objects.filter(is_read=False).update(is_read=True)
    return Response(data)


@api_view(['GET'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_report_unread(request):
    count = CommentReport.objects.filter(is_read=False).count()
    return Response({'unread': count})


@api_view(['POST'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_report_dismiss(request, report_id):
    report = get_object_or_404(CommentReport, pk=report_id)
    report.delete()
    return Response({'ok': True})


@api_view(['POST'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAdminUser])
def staff_report_delete_comment(request, report_id):
    report = get_object_or_404(CommentReport, pk=report_id)
    report.comment.delete()  # cascades to report
    return Response({'ok': True})


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
    img = ProductImage.objects.create(product=product, image_key=image_key)
    return JsonResponse({'ok': True, 'id': img.id})


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
    categories = Category.objects.annotate(product_count=Count('products')).order_by('order', 'name')
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
            from django.db.models import Max
            max_order = Category.objects.aggregate(Max('order'))['order__max'] or 0
            cat = Category.objects.create(name=name, cover_key=cover_key, order=max_order + 1)
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
    images = list(product.images.all().order_by('order', 'created_at', 'id'))
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
def mobile2_category_edit(request, pk):
    category = get_object_or_404(Category, pk=pk)
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        if name:
            category.name = name
            category.save(update_fields=['name'])
        return redirect('mobile2_category_detail', pk=pk)
    return render(request, 'mobile2/category_edit.html', {'category': category})


@staff_member_required
@require_http_methods(['POST'])
def mobile2_category_delete(request, pk):
    category = get_object_or_404(Category, pk=pk)
    category.delete()
    return redirect('mobile2_home')


@staff_member_required
def mobile2_product_edit(request, pk):
    product = get_object_or_404(Product, pk=pk)
    if request.method == 'POST':
        name = request.POST.get('name', '').strip()
        description = request.POST.get('description', '').strip()
        if name:
            product.name = name
            product.description = description
            product.save(update_fields=['name', 'description'])
        return redirect('mobile2_product_detail', pk=pk)
    return render(request, 'mobile2/product_edit.html', {'product': product})


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
def mobile2_product_images_action(request, pk):
    """Bulk action on selected ProductImages: hide, show, delete, move."""
    product = get_object_or_404(Product, pk=pk)
    action = request.POST.get('action')
    ids = [int(i) for i in request.POST.getlist('ids[]') if i.isdigit()]
    if not ids or action not in ('hide', 'show', 'delete', 'move'):
        return redirect('mobile2_product_detail', pk=pk)

    images = list(ProductImage.objects.filter(id__in=ids, product=product))
    if not images:
        return redirect('mobile2_product_detail', pk=pk)

    selected_keys = {img.image_key for img in images}

    def _update_cover(prod, excluded_keys):
        """If product cover is among excluded_keys, switch to first remaining visible."""
        if prod.cover_key in excluded_keys:
            first = prod.images.filter(is_hidden=False).exclude(image_key__in=excluded_keys).first()
            prod.cover_key = first.image_key if first else ''
            prod.save(update_fields=['cover_key'])

    if action == 'hide':
        _update_cover(product, selected_keys)
        ProductImage.objects.filter(id__in=ids).update(is_hidden=True)
        return redirect('mobile2_product_detail', pk=pk)

    if action == 'show':
        ProductImage.objects.filter(id__in=ids).update(is_hidden=False)
        return redirect('mobile2_product_detail', pk=pk)

    if action == 'delete':
        _update_cover(product, selected_keys)
        for key in selected_keys:
            _delete_s3_image_and_thumbnails(key)
        ProductImage.objects.filter(id__in=ids).delete()
        return redirect('mobile2_product_detail', pk=pk)

    if action == 'move':
        first_image = images[0]
        new_product = Product.objects.create(
            name=_random_product_name(),
            cover_key=first_image.image_key,
        )
        # Copy categories from original product
        for cat in product.categories.all():
            new_product.categories.add(cat)
        # Move images to new product
        ProductImage.objects.filter(id__in=ids).update(product=new_product)
        # Update cover of original if needed
        _update_cover(product, selected_keys)
        return redirect('mobile2_product_detail', pk=new_product.pk)


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


# ── Избранное ─────────────────────────────────────────────────────────────────

@api_view(['GET', 'POST'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def favorites(request):
    if request.method == 'GET':
        favs = FavoriteImage.objects.filter(user=request.user).select_related('image', 'image__product')
        return Response(FavoriteImageSerializer(favs, many=True).data)

    image_id = request.data.get('image_id')
    if not image_id:
        return Response({'error': 'image_id required'}, status=status.HTTP_400_BAD_REQUEST)
    image = get_object_or_404(ProductImage, pk=image_id, product__is_hidden=False)
    _, created = FavoriteImage.objects.get_or_create(user=request.user, image=image)
    return Response({'status': 'ok'}, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)


@api_view(['DELETE'])
@authentication_classes([TokenAuthentication])
@permission_classes([IsAuthenticated])
def favorite_delete(request, image_id):
    FavoriteImage.objects.filter(user=request.user, image_id=image_id).delete()
    return Response(status=status.HTTP_204_NO_CONTENT)


def latest_app_version(request):
    release = AppRelease.objects.order_by('-version_code').first()
    if not release:
        return JsonResponse({'error': 'No releases'}, status=404)
    return JsonResponse({
        'version_code': release.version_code,
        'version_name': release.version_name,
        'download_page_url': 'https://eliza.gallery/app/download/',
    })


def app_download(request):
    releases = list(AppRelease.objects.order_by('-version_code'))
    latest = releases[0] if releases else None
    apk_url = ''
    if latest and latest.apk_key:
        base = settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')
        apk_url = f'{base}/{latest.apk_key}'
    return render(request, 'app_download.html', {
        'latest': latest,
        'releases': releases,
        'apk_url': apk_url,
    })


@staff_member_required
def log_view(request):
    log_file = settings.REQUEST_LOG_FILE
    lines = []
    if log_file and os.path.exists(log_file):
        with open(log_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()
    log_text = ''.join(reversed(lines[-5000:]))
    return render(request, 'log.html', {'log': log_text})


@staff_member_required
def mobile2_category_reorder(request):
    from django.db.models import Count
    if request.method == 'POST':
        ids = request.POST.getlist('order[]')
        for i, cat_id in enumerate(ids):
            Category.objects.filter(pk=cat_id).update(order=i)
        return redirect('mobile2_home')
    categories = Category.objects.annotate(product_count=Count('products')).order_by('order', 'name')
    return render(request, 'mobile2/category_order.html', {
        'categories': categories,
        'public_base': PUBLIC_BASE,
    })
