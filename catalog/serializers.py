from rest_framework import serializers
from django.conf import settings
from .models import Category, Product, ProductImage


def _public_url(key):
    if not key:
        return None
    base = settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')
    return f'{base}/{key}'


class CategorySerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()

    class Meta:
        model = Category
        fields = ['id', 'name', 'cover_url']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)


class ProductImageSerializer(serializers.ModelSerializer):
    image_url = serializers.SerializerMethodField()

    class Meta:
        model = ProductImage
        fields = ['id', 'image_url', 'order']

    def get_image_url(self, obj):
        return _public_url(obj.image_key)


class ProductListSerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()

    class Meta:
        model = Product
        fields = ['id', 'name', 'cover_url', 'created_at']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)


class ProductDetailSerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()
    images = ProductImageSerializer(many=True, read_only=True)

    class Meta:
        model = Product
        fields = ['id', 'name', 'description', 'cover_url', 'created_at', 'images']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)
