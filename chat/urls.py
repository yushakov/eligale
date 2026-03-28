from django.urls import path
from . import views

urlpatterns = [
    # Пользователь
    path('api/chat/', views.chat_info, name='chat_info'),
    path('api/chat/messages/', views.chat_messages, name='chat_messages'),
    path('api/chat/messages/send/', views.chat_send, name='chat_send'),
    path('api/chat/mark-read/', views.chat_mark_read, name='chat_mark_read'),
    path('api/chat/unread/', views.chat_unread, name='chat_unread'),

    # Staff
    path('api/chats/', views.staff_chat_list, name='staff_chat_list'),
    path('api/chats/unread/', views.staff_unread, name='staff_unread'),
    path('api/chats/<int:user_id>/messages/', views.staff_chat_messages, name='staff_chat_messages'),
    path('api/chats/<int:user_id>/messages/send/', views.staff_chat_send, name='staff_chat_send'),
    path('api/chats/<int:user_id>/mark-read/', views.staff_chat_mark_read, name='staff_chat_mark_read'),
]
