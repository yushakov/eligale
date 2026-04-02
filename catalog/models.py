from django.conf import settings
from django.db import models


class Category(models.Model):
    name = models.CharField(max_length=255)
    cover_key = models.CharField(max_length=1024, blank=True, default='')
    is_hidden = models.BooleanField(default=False)
    order = models.IntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'Category'
        verbose_name_plural = 'Categories'
        ordering = ['order', 'name']

    def __str__(self):
        return self.name


class Product(models.Model):
    categories = models.ManyToManyField(Category, related_name='products', blank=True)
    name = models.CharField(max_length=255)
    description = models.TextField(blank=True, default='')
    cover_key = models.CharField(max_length=1024, blank=True, default='')
    is_hidden = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'Product'
        verbose_name_plural = 'Products'
        ordering = ['-created_at']

    def __str__(self):
        return self.name


class ProductImage(models.Model):
    product = models.ForeignKey(Product, on_delete=models.CASCADE, related_name='images')
    image_key = models.CharField(max_length=1024)
    order = models.PositiveIntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'Product Image'
        verbose_name_plural = 'Product Images'
        ordering = ['order', 'created_at']

    def __str__(self):
        return f'{self.product.name} — image {self.order}'


class FavoriteImage(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='favorite_images')
    image = models.ForeignKey(ProductImage, on_delete=models.CASCADE, related_name='favorited_by')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('user', 'image')
        ordering = ['-created_at']

    def __str__(self):
        return f'{self.user.email} ♥ image {self.image.id}'


class Comment(models.Model):
    product = models.ForeignKey(Product, on_delete=models.CASCADE, related_name='comments')
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='comments')
    text = models.TextField()
    is_read_by_staff = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'Comment'
        verbose_name_plural = 'Comments'
        ordering = ['created_at']

    def __str__(self):
        return f'{self.user.email} → {self.product.name}'


class AppRelease(models.Model):
    version_code = models.IntegerField(unique=True)
    version_name = models.CharField(max_length=20)
    apk_key = models.CharField(max_length=1024, blank=True, default='')
    release_notes = models.TextField(blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = 'App Release'
        verbose_name_plural = 'App Releases'
        ordering = ['-version_code']

    def __str__(self):
        return f'v{self.version_name} (code {self.version_code})'


class CommentReport(models.Model):
    comment = models.ForeignKey(Comment, on_delete=models.CASCADE, related_name='reports')
    reporter = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='comment_reports')
    text = models.CharField(max_length=150)
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ('comment', 'reporter')
        ordering = ['-created_at']

    def __str__(self):
        return f'{self.reporter.email} → comment {self.comment.id}'
