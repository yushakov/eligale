import mimetypes
import uuid

import boto3
from botocore.config import Config
from django.conf import settings
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from rest_framework.authentication import TokenAuthentication

from .models import Chat, ChatMessage
from .serializers import ChatMessageSerializer, ChatListSerializer


def _auth(request):
    try:
        user, _ = TokenAuthentication().authenticate(request)
        return user
    except Exception:
        return None


def _require_auth(request):
    user = _auth(request)
    if user is None:
        return None, Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)
    return user, None


def _require_staff(request):
    user, err = _require_auth(request)
    if err:
        return None, err
    if not user.is_staff:
        return None, Response({'error': 'Staff only'}, status=status.HTTP_403_FORBIDDEN)
    return user, None


def _get_or_create_chat(user):
    chat, _ = Chat.objects.get_or_create(user=user)
    return chat


def _send_message(chat, sender, text):
    msg = ChatMessage.objects.create(chat=chat, sender=sender, text=text)
    chat.last_message_at = msg.created_at
    chat.save(update_fields=['last_message_at'])
    return msg


def _fetch_messages(qs, after, before, limit):
    if after:
        qs = qs.filter(id__gt=int(after)).order_by('created_at')[:limit]
    elif before:
        qs = list(reversed(list(qs.filter(id__lt=int(before)).order_by('-created_at')[:limit])))
    else:
        qs = list(reversed(list(qs.order_by('-created_at')[:limit])))
    return qs


# ── Медиа в чате ──────────────────────────────────────────────────────────────

@api_view(['POST'])
def chat_media_presign(request):
    """
    Presign-URL для загрузки медиафайла в публичный бакет (папка chat/).
    Доступен любому авторизованному пользователю (и user, и staff).
    Body: { "filename": "photo.jpg" }
    Response: { "upload_url": "...", "public_url": "..." }
    """
    user, err = _require_auth(request)
    if err:
        return err

    filename = request.data.get('filename', 'image.jpg')
    ext = filename.rsplit('.', 1)[-1].lower() if '.' in filename else 'jpg'
    key = f"chat/{uuid.uuid4().hex}.{ext}"

    content_type = mimetypes.guess_type(filename)[0] or 'application/octet-stream'

    session = boto3.session.Session()
    s3 = session.client(
        service_name='s3',
        endpoint_url='https://storage.yandexcloud.net',
        aws_access_key_id=settings.YA_PUBLIC_UPLOADER_ACCESS_KEY_ID,
        aws_secret_access_key=settings.YA_PUBLIC_UPLOADER_SECRET_ACCESS_KEY,
        region_name=settings.YA_PUBLIC_UPLOADER_REGION_NAME,
        config=Config(signature_version='s3v4'),
    )
    upload_url = s3.generate_presigned_url(
        ClientMethod='put_object',
        Params={
            'Bucket': settings.YA_PUBLIC_UPLOADER_BUCKET_NAME,
            'Key': key,
            'ContentType': content_type,
        },
        ExpiresIn=300,
        HttpMethod='PUT',
    )
    public_url = f"{settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')}/{key}"
    return Response({'upload_url': upload_url, 'public_url': public_url})


# ── Пользовательские эндпоинты ────────────────────────────────────────────────

@api_view(['GET'])
def chat_info(request):
    """Инфо о чате: id чата и id последнего сообщения."""
    user, err = _require_auth(request)
    if err:
        return err
    chat = _get_or_create_chat(user)
    last = chat.messages.order_by('-id').first()
    return Response({
        'chat_id': chat.id,
        'last_message_id': last.id if last else None,
    })


@api_view(['GET'])
def chat_messages(request):
    """
    Сообщения чата пользователя.
    ?after=<id>  — новые (polling)
    ?before=<id> — старые (история)
    &limit=<n>   — кол-во (default 50)
    """
    user, err = _require_auth(request)
    if err:
        return err
    chat = _get_or_create_chat(user)
    limit = min(int(request.query_params.get('limit', 50)), 100)
    after = request.query_params.get('after')
    before = request.query_params.get('before')
    qs = _fetch_messages(chat.messages.select_related('sender'), after, before, limit)
    return Response(ChatMessageSerializer(qs, many=True).data)


@api_view(['POST'])
def chat_send(request):
    """Отправить сообщение в свой чат."""
    user, err = _require_auth(request)
    if err:
        return err
    text = request.data.get('text', '').strip()
    if not text:
        return Response({'error': 'text is required'}, status=status.HTTP_400_BAD_REQUEST)
    chat = _get_or_create_chat(user)
    msg = _send_message(chat, sender=user, text=text)
    return Response(ChatMessageSerializer(msg).data, status=status.HTTP_201_CREATED)


@api_view(['POST'])
def chat_mark_read(request):
    """Пометить прочитанными все сообщения от staff до up_to_id включительно."""
    user, err = _require_auth(request)
    if err:
        return err
    up_to_id = request.data.get('up_to_id')
    if not up_to_id:
        return Response({'error': 'up_to_id is required'}, status=status.HTTP_400_BAD_REQUEST)
    chat = _get_or_create_chat(user)
    chat.messages.filter(id__lte=int(up_to_id), is_read=False).exclude(sender=user).update(is_read=True)
    return Response({'ok': True})


@api_view(['GET'])
def chat_unread(request):
    """Количество непрочитанных сообщений от staff."""
    user, err = _require_auth(request)
    if err:
        return err
    try:
        count = user.chat.messages.filter(is_read=False).exclude(sender=user).count()
    except Chat.DoesNotExist:
        count = 0
    return Response({'unread': count})


# ── Staff эндпоинты ───────────────────────────────────────────────────────────

@api_view(['GET'])
def staff_chat_list(request):
    """Список всех чатов, отсортированных по дате последнего сообщения."""
    _, err = _require_staff(request)
    if err:
        return err
    chats = Chat.objects.select_related('user').order_by('-last_message_at')
    return Response(ChatListSerializer(chats, many=True).data)


@api_view(['GET'])
def staff_chat_messages(request, user_id):
    """Сообщения конкретного чата (те же параметры after/before)."""
    _, err = _require_staff(request)
    if err:
        return err
    chat = Chat.objects.filter(user_id=user_id).first()
    if not chat:
        return Response({'error': 'Chat not found'}, status=status.HTTP_404_NOT_FOUND)
    limit = min(int(request.query_params.get('limit', 50)), 100)
    after = request.query_params.get('after')
    before = request.query_params.get('before')
    qs = _fetch_messages(chat.messages.select_related('sender'), after, before, limit)
    return Response(ChatMessageSerializer(qs, many=True).data)


@api_view(['POST'])
def staff_chat_send(request, user_id):
    """Staff отправляет сообщение пользователю."""
    staff_user, err = _require_staff(request)
    if err:
        return err
    text = request.data.get('text', '').strip()
    if not text:
        return Response({'error': 'text is required'}, status=status.HTTP_400_BAD_REQUEST)
    chat = Chat.objects.filter(user_id=user_id).first()
    if not chat:
        return Response({'error': 'Chat not found'}, status=status.HTTP_404_NOT_FOUND)
    msg = _send_message(chat, sender=staff_user, text=text)
    return Response(ChatMessageSerializer(msg).data, status=status.HTTP_201_CREATED)


@api_view(['POST'])
def staff_chat_mark_read(request, user_id):
    """Staff помечает сообщения от пользователя как прочитанные."""
    _, err = _require_staff(request)
    if err:
        return err
    up_to_id = request.data.get('up_to_id')
    if not up_to_id:
        return Response({'error': 'up_to_id is required'}, status=status.HTTP_400_BAD_REQUEST)
    chat = Chat.objects.filter(user_id=user_id).first()
    if not chat:
        return Response({'error': 'Chat not found'}, status=status.HTTP_404_NOT_FOUND)
    chat.messages.filter(id__lte=int(up_to_id), sender_id=user_id, is_read=False).update(is_read=True)
    return Response({'ok': True})


@api_view(['GET'])
def staff_unread(request):
    """Общее количество непрочитанных сообщений от всех пользователей."""
    _, err = _require_staff(request)
    if err:
        return err
    count = ChatMessage.objects.filter(is_read=False, sender__is_staff=False).count()
    return Response({'unread': count})
