from django.urls import path
from . import views

urlpatterns = [
    path('api/catalog/upload/presign', views.presign_upload, name='catalog_presign_upload'),
]
