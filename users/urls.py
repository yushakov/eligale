from django.urls import path
from . import views

urlpatterns = [
    path('api/auth/request-code/', views.request_code, name='auth_request_code'),
    path('api/auth/verify-code/', views.verify_code, name='auth_verify_code'),
]
