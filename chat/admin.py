from django.contrib import admin
from .models import Chat, ChatMessage


class ChatMessageInline(admin.TabularInline):
    model = ChatMessage
    extra = 0
    readonly_fields = ['sender', 'text', 'is_read', 'created_at']


@admin.register(Chat)
class ChatAdmin(admin.ModelAdmin):
    list_display = ['user', 'last_message_at', 'created_at']
    inlines = [ChatMessageInline]


@admin.register(ChatMessage)
class ChatMessageAdmin(admin.ModelAdmin):
    list_display = ['chat', 'sender', 'text', 'is_read', 'created_at']
    list_filter = ['is_read']
