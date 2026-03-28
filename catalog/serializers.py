from rest_framework import serializers
from django.conf import settings
from .models import Category, Product, ProductImage, Comment


def _public_url(key):
    if not key:
        return None
    base = settings.YA_PUBLIC_UPLOADER_PUBLIC_BASE_URL.rstrip('/')
    return f'{base}/{key}'


class CategorySerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()
    product_count = serializers.SerializerMethodField()

    class Meta:
        model = Category
        fields = ['id', 'name', 'cover_url', 'product_count']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)

    def get_product_count(self, obj):
        return obj.products.filter(is_hidden=False).count()


class ProductImageSerializer(serializers.ModelSerializer):
    image_url = serializers.SerializerMethodField()

    class Meta:
        model = ProductImage
        fields = ['id', 'image_url', 'order']

    def get_image_url(self, obj):
        return _public_url(obj.image_key)


class ProductListSerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()
    image_count = serializers.SerializerMethodField()

    class Meta:
        model = Product
        fields = ['id', 'name', 'cover_url', 'created_at', 'image_count']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)

    def get_image_count(self, obj):
        return obj.images.count()


class ProductDetailSerializer(serializers.ModelSerializer):
    cover_url = serializers.SerializerMethodField()
    images = ProductImageSerializer(many=True, read_only=True)

    class Meta:
        model = Product
        fields = ['id', 'name', 'description', 'cover_url', 'created_at', 'images']

    def get_cover_url(self, obj):
        return _public_url(obj.cover_key)


class CommentSerializer(serializers.ModelSerializer):
    author = serializers.SerializerMethodField()
    user_id = serializers.IntegerField(source='user.id', read_only=True)
    user_email = serializers.EmailField(source='user.email', read_only=True)

    class Meta:
        model = Comment
        fields = ['id', 'user_id', 'user_email', 'author', 'text', 'created_at']

    def get_author(self, obj):
        return obj.user.display_name or obj.user.email.split('@')[0]
