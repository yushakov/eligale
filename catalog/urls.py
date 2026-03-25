from django.urls import path
from . import views

urlpatterns = [
    path('api/catalog/upload/presign', views.presign_upload, name='catalog_presign_upload'),
    path('api/categories/', views.category_list, name='category_list'),
    path('api/categories/<int:category_id>/products/', views.product_list, name='product_list'),
    path('api/products/<int:product_id>/', views.product_detail, name='product_detail'),
]
