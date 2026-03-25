from django import forms
from django.contrib import admin
from .models import Category, Product, ProductImage
from .widgets import ImageUploadWidget


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


class ProductInline(admin.TabularInline):
    model = Product
    extra = 1
    fields = ['name', 'cover_key']
    show_change_link = True


class CategoryForm(forms.ModelForm):
    class Meta:
        model = Category
        fields = '__all__'
        widgets = {'cover_key': ImageUploadWidget}


@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    form = CategoryForm
    list_display = ['name', 'created_at']
    inlines = [ProductInline]


@admin.register(Product)
class ProductAdmin(admin.ModelAdmin):
    form = ProductForm
    list_display = ['name', 'category', 'created_at']
    list_filter = ['category']
    inlines = [ProductImageInline]


@admin.register(ProductImage)
class ProductImageAdmin(admin.ModelAdmin):
    list_display = ['product', 'order', 'created_at']
    list_filter = ['product__category']
