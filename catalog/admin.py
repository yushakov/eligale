from django import forms
from django.contrib import admin
from django.db.models import Max
from .models import AppRelease, Category, Product, ProductImage, Comment
from .widgets import ApkUploadWidget, ImageUploadWidget


class ProductImageInlineForm(forms.ModelForm):
    class Meta:
        model = ProductImage
        fields = '__all__'
        widgets = {'image_key': ImageUploadWidget}


class ProductImageInline(admin.TabularInline):
    model = ProductImage
    form = ProductImageInlineForm
    extra = 3
    fields = ['image_key', 'order']


class ProductForm(forms.ModelForm):
    class Meta:
        model = Product
        fields = '__all__'
        widgets = {'cover_key': ImageUploadWidget}


class CategoryForm(forms.ModelForm):
    class Meta:
        model = Category
        fields = '__all__'
        widgets = {'cover_key': ImageUploadWidget}


@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    form = CategoryForm
    list_display = ['name', 'created_at']


@admin.register(Product)
class ProductAdmin(admin.ModelAdmin):
    form = ProductForm
    list_display = ['name', 'created_at']
    filter_horizontal = ['categories']
    inlines = [ProductImageInline]


@admin.register(ProductImage)
class ProductImageAdmin(admin.ModelAdmin):
    list_display = ['product', 'order', 'created_at']


@admin.register(Comment)
class CommentAdmin(admin.ModelAdmin):
    list_display = ['product', 'user', 'text', 'created_at']
    readonly_fields = ['created_at']


class AppReleaseForm(forms.ModelForm):
    class Meta:
        model = AppRelease
        fields = '__all__'
        widgets = {'apk_key': ApkUploadWidget}

    def clean_version_code(self):
        code = self.cleaned_data['version_code']
        qs = AppRelease.objects.all()
        if self.instance.pk:
            qs = qs.exclude(pk=self.instance.pk)
        max_code = qs.aggregate(Max('version_code'))['version_code__max']
        if max_code is not None and code <= max_code:
            raise forms.ValidationError(
                f'version_code должен быть больше текущего максимума ({max_code})'
            )
        return code


@admin.register(AppRelease)
class AppReleaseAdmin(admin.ModelAdmin):
    form = AppReleaseForm
    list_display = ['version_name', 'version_code', 'apk_key', 'created_at']
    readonly_fields = ['created_at']
