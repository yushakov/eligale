import logging
import random
import uuid
from datetime import timedelta

logger = logging.getLogger(__name__)

from django.contrib.auth import get_user_model
from django.core.mail import send_mail
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from rest_framework.authentication import TokenAuthentication
from rest_framework.authtoken.models import Token

from .models import EmailVerification


def _unique_display_name(name: str) -> str:
    """Возвращает уникальное display_name, добавляя хэш если имя занято."""
    if not User.objects.filter(display_name=name).exists():
        return name
    while True:
        candidate = f'{name} #{uuid.uuid4().hex[:4]}'
        if not User.objects.filter(display_name=candidate).exists():
            return candidate

User = get_user_model()

CODE_TTL_MINUTES = 15


@api_view(['POST'])
def request_code(request):
    email = request.data.get('email', '').strip().lower()
    if not email:
        return Response({'error': 'email is required'}, status=status.HTTP_400_BAD_REQUEST)

    code = f'{random.randint(0, 999999):06d}'
    EmailVerification.objects.create(email=email, code=code)

    logger.info('Verification code for %s: %s', email, code)

    send_mail(
        subject='Ваш код подтверждения — Eliza Gallery',
        message=f'Ваш код: {code}\n\nКод действителен {CODE_TTL_MINUTES} минут.',
        from_email='noreply@otp.eliza.gallery',
        recipient_list=[email],
        fail_silently=False,
    )

    return Response({'ok': True})


@api_view(['POST'])
def verify_code(request):
    email = request.data.get('email', '').strip().lower()
    code = request.data.get('code', '').strip()

    if not email or not code:
        return Response({'error': 'email and code are required'}, status=status.HTTP_400_BAD_REQUEST)

    cutoff = timezone.now() - timedelta(minutes=CODE_TTL_MINUTES)
    verification = EmailVerification.objects.filter(
        email=email,
        code=code,
        is_used=False,
        created_at__gte=cutoff,
    ).order_by('-created_at').first()

    if not verification:
        return Response({'error': 'Invalid or expired code'}, status=status.HTTP_400_BAD_REQUEST)

    verification.is_used = True
    verification.save(update_fields=['is_used'])

    user, _ = User.objects.get_or_create(email=email)
    token, _ = Token.objects.get_or_create(user=user)

    return Response({'token': token.key, 'has_name': bool(user.display_name)})


@api_view(['POST'])
def set_name(request):
    auth = TokenAuthentication()
    try:
        user, _ = auth.authenticate(request)
    except Exception:
        return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)

    name = request.data.get('name', '').strip()
    if not name:
        return Response({'error': 'name is required'}, status=status.HTTP_400_BAD_REQUEST)

    if name == user.display_name:
        return Response({'display_name': user.display_name})

    display_name = _unique_display_name(name)
    user.display_name = display_name
    user.save(update_fields=['display_name'])

    return Response({'display_name': display_name})


@api_view(['GET'])
def profile(request):
    auth = TokenAuthentication()
    try:
        user, _ = auth.authenticate(request)
    except Exception:
        return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)

    return Response({'email': user.email, 'display_name': user.display_name or ''})


@api_view(['DELETE'])
def delete_account(request):
    auth = TokenAuthentication()
    try:
        user, _ = auth.authenticate(request)
    except Exception:
        return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)

    if user.is_staff:
        return Response({'error': 'Staff accounts cannot be deleted via API'}, status=status.HTTP_403_FORBIDDEN)

    user.delete()
    return Response({'ok': True})
