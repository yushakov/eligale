from rest_framework import serializers
from .models import Chat, ChatMessage


class ChatMessageSerializer(serializers.ModelSerializer):
    sender_email = serializers.EmailField(source='sender.email', read_only=True)
    is_staff = serializers.BooleanField(source='sender.is_staff', read_only=True)

    class Meta:
        model = ChatMessage
        fields = ['id', 'sender_email', 'is_staff', 'text', 'is_read', 'created_at']


class ChatListSerializer(serializers.ModelSerializer):
    user_id = serializers.IntegerField(source='user.id', read_only=True)
    user_email = serializers.EmailField(source='user.email', read_only=True)
    user_display_name = serializers.CharField(source='user.display_name', read_only=True)
    unread_count = serializers.SerializerMethodField()
    last_message = serializers.SerializerMethodField()

    class Meta:
        model = Chat
        fields = ['id', 'user_id', 'user_email', 'user_display_name', 'unread_count', 'last_message', 'last_message_at']

    def get_unread_count(self, obj):
        # Для staff: непрочитанные сообщения от пользователя
        return obj.messages.filter(sender=obj.user, is_read=False).count()

    def get_last_message(self, obj):
        msg = obj.messages.order_by('-created_at').first()
        if msg:
            return {'text': msg.text[:80], 'created_at': msg.created_at}
        return None
