from django.urls import path
from . import views

urlpatterns = [
    path("test-image-upload", views.test_image_upload_page, name="test_image_upload_page"),
    path("api/test-image-upload", views.test_image_upload_api, name="test_image_upload_api"),
    path("api/test-image-upload/presign", views.test_image_upload_presign_api, name="test_image_upload_presign_api"),
]
