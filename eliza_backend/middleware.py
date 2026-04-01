import threading
from datetime import datetime

from django.conf import settings

_lock = threading.Lock()


class RequestLogMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)

        log_file = getattr(settings, 'REQUEST_LOG_FILE', None)
        if not log_file:
            return response

        # IP — берём из X-Forwarded-For (nginx), иначе REMOTE_ADDR
        forwarded = request.META.get('HTTP_X_FORWARDED_FOR', '')
        ip = forwarded.split(',')[0].strip() if forwarded else request.META.get('REMOTE_ADDR', '-')

        # Email авторизованного пользователя
        email = '-'
        if hasattr(request, 'user') and request.user.is_authenticated:
            email = request.user.email

        now = datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')
        line = f"{now}  {ip}  {email}  {request.method}  {request.get_full_path()}  {response.status_code}\n"

        try:
            with _lock:
                with open(log_file, 'a', encoding='utf-8') as f:
                    f.write(line)
        except OSError:
            pass

        return response
