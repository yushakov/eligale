from rest_framework import serializers
from django.conf import settings
from .models import Category, Product, ProductImage, Comment
from .thumbnails import thumbnail_key


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
    image_url_100 = serializers.SerializerMethodField()
    image_url_200 = serializers.SerializerMethodField()
    image_url_300 = serializers.SerializerMethodField()

    class Meta:
        model = ProductImage
        fields = ['id', 'image_url', 'image_url_100', 'image_url_200', 'image_url_300', 'order']

    def get_image_url(self, obj):
        return _public_url(obj.image_key)

    def get_image_url_100(self, obj):
        return _public_url(thumbnail_key(obj.image_key, 100))

    def get_image_url_200(self, obj):
        return _public_url(thumbnail_key(obj.image_key, 200))

    def get_image_url_300(self, obj):
        return _public_url(thumbnail_key(obj.image_key, 300))


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


class StaffCommentSerializer(serializers.ModelSerializer):
    user_id = serializers.IntegerField(source='user.id', read_only=True)
    user_email = serializers.EmailField(source='user.email', read_only=True)
    user_display_name = serializers.CharField(source='user.display_name', read_only=True, allow_null=True)
    product_id = serializers.IntegerField(source='product.id', read_only=True)
    product_name = serializers.CharField(source='product.name', read_only=True)

    class Meta:
        model = Comment
        fields = ['id', 'user_id', 'user_email', 'user_display_name', 'product_id', 'product_name', 'text', 'is_read_by_staff', 'created_at']
