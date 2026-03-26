import random
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.mail import send_mail
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from rest_framework.authtoken.models import Token

from .models import EmailVerification

User = get_user_model()

CODE_TTL_MINUTES = 15


@api_view(['POST'])
def request_code(request):
    email = request.data.get('email', '').strip().lower()
    if not email:
        return Response({'error': 'email is required'}, status=status.HTTP_400_BAD_REQUEST)

    code = f'{random.randint(0, 999999):06d}'
    EmailVerification.objects.create(email=email, code=code)

    send_mail(
        subject='Ваш код подтверждения — Eliza Gallery',
        message=f'Ваш код: {code}\n\nКод действителен {CODE_TTL_MINUTES} минут.',
        from_email='noreply@eliza.gallery',
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

    return Response({'token': token.key})
