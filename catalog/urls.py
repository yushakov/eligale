from django.urls import path
from . import views

urlpatterns = [
    # API
    path('api/catalog/upload/presign', views.presign_upload, name='catalog_presign_upload'),
    path('api/categories/', views.category_list, name='category_list'),
    path('api/categories/<int:category_id>/products/', views.product_list, name='product_list'),
    path('api/products/<int:product_id>/', views.product_detail, name='product_detail'),
    path('api/products/<int:product_id>/comments/', views.comment_list, name='comment_list'),

    # Mobile
    path('mobile/', views.mobile_home, name='mobile_home'),
    path('mobile/categories/add/', views.mobile_category_add, name='mobile_category_add'),
    path('mobile/categories/<int:pk>/', views.mobile_category_detail, name='mobile_category_detail'),
    path('mobile/categories/<int:category_id>/products/add/', views.mobile_product_add, name='mobile_product_add'),
    path('mobile/products/<int:pk>/', views.mobile_product_detail, name='mobile_product_detail'),
    path('mobile/products/<int:pk>/images/add/', views.mobile_product_image_add, name='mobile_product_image_add'),
    # Mobile2
    path('mobile2/', views.mobile2_home, name='mobile2_home'),
    path('mobile2/categories/add/', views.mobile2_category_add, name='mobile2_category_add'),
    path('mobile2/categories/<int:pk>/', views.mobile2_category_detail, name='mobile2_category_detail'),
    path('mobile2/categories/<int:category_id>/products/add/', views.mobile2_product_add, name='mobile2_product_add'),
    path('mobile2/products/<int:pk>/', views.mobile2_product_detail, name='mobile2_product_detail'),

    path('mobile/categories/<int:pk>/toggle-hidden/', views.mobile_category_toggle_hidden, name='mobile_category_toggle_hidden'),
    path('mobile/products/<int:pk>/toggle-hidden/', views.mobile_product_toggle_hidden, name='mobile_product_toggle_hidden'),
    path('mobile/products/<int:pk>/toggle-category/<int:category_id>/', views.mobile_product_toggle_category, name='mobile_product_toggle_category'),
]
