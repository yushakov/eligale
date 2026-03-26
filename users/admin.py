from django.contrib import admin
from django.contrib.auth.admin import UserAdmin as BaseUserAdmin
from .models import User, EmailVerification


@admin.register(User)
class UserAdmin(BaseUserAdmin):
    ordering = ['email']
    list_display = ['email', 'display_name', 'is_staff', 'is_active', 'date_joined']
    fieldsets = (
        (None, {'fields': ('email', 'display_name', 'password')}),
        ('Permissions', {'fields': ('is_active', 'is_staff', 'is_superuser', 'groups', 'user_permissions')}),
    )
    add_fieldsets = (
        (None, {'fields': ('email', 'password1', 'password2')}),
    )
    search_fields = ['email']
    filter_horizontal = ('groups', 'user_permissions')


@admin.register(EmailVerification)
class EmailVerificationAdmin(admin.ModelAdmin):
    list_display = ['email', 'code', 'is_used', 'created_at']
    readonly_fields = ['created_at']
