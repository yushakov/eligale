from django.test import TestCase, Client
from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from .models import Chat, ChatMessage

User = get_user_model()


class ChatTestBase(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com', password='pass')
        self.staff = User.objects.create_superuser(email='staff@example.com', password='pass')
        self.user_token = Token.objects.create(user=self.user)
        self.staff_token = Token.objects.create(user=self.staff)

    def auth(self, token):
        return {'HTTP_AUTHORIZATION': f'Token {token.key}'}

    def user_auth(self):
        return self.auth(self.user_token)

    def staff_auth(self):
        return self.auth(self.staff_token)

    def make_chat(self, user=None):
        return Chat.objects.create(user=user or self.user)

    def make_message(self, chat, sender, text='Привет', is_read=False):
        msg = ChatMessage.objects.create(chat=chat, sender=sender, text=text, is_read=is_read)
        chat.last_message_at = msg.created_at
        chat.save(update_fields=['last_message_at'])
        return msg


# ── chat_info ─────────────────────────────────────────────────────────────────

class ChatInfoTest(ChatTestBase):
    def test_requires_auth(self):
        r = self.client.get('/api/chat/')
        self.assertEqual(r.status_code, 401)

    def test_creates_chat_if_not_exists(self):
        self.client.get('/api/chat/', **self.user_auth())
        self.assertTrue(Chat.objects.filter(user=self.user).exists())

    def test_returns_chat_id(self):
        r = self.client.get('/api/chat/', **self.user_auth())
        self.assertIn('chat_id', r.json())

    def test_last_message_id_none_when_empty(self):
        r = self.client.get('/api/chat/', **self.user_auth())
        self.assertIsNone(r.json()['last_message_id'])

    def test_last_message_id_reflects_latest(self):
        chat = self.make_chat()
        msg = self.make_message(chat, self.staff)
        r = self.client.get('/api/chat/', **self.user_auth())
        self.assertEqual(r.json()['last_message_id'], msg.id)


# ── chat_messages ─────────────────────────────────────────────────────────────

class ChatMessagesTest(ChatTestBase):
    def setUp(self):
        super().setUp()
        self.chat = self.make_chat()
        self.msg1 = self.make_message(self.chat, self.staff, 'Первое')
        self.msg2 = self.make_message(self.chat, self.user, 'Второе')
        self.msg3 = self.make_message(self.chat, self.staff, 'Третье')

    def test_requires_auth(self):
        r = self.client.get('/api/chat/messages/')
        self.assertEqual(r.status_code, 401)

    def test_returns_messages_newest_first_by_default(self):
        r = self.client.get('/api/chat/messages/', **self.user_auth())
        ids = [m['id'] for m in r.json()]
        self.assertEqual(ids, [self.msg1.id, self.msg2.id, self.msg3.id])

    def test_after_returns_newer_messages(self):
        r = self.client.get(f'/api/chat/messages/?after={self.msg1.id}', **self.user_auth())
        ids = [m['id'] for m in r.json()]
        self.assertNotIn(self.msg1.id, ids)
        self.assertIn(self.msg2.id, ids)
        self.assertIn(self.msg3.id, ids)

    def test_before_returns_older_messages(self):
        r = self.client.get(f'/api/chat/messages/?before={self.msg3.id}', **self.user_auth())
        ids = [m['id'] for m in r.json()]
        self.assertNotIn(self.msg3.id, ids)
        self.assertIn(self.msg1.id, ids)
        self.assertIn(self.msg2.id, ids)

    def test_message_has_expected_fields(self):
        r = self.client.get('/api/chat/messages/', **self.user_auth())
        msg = r.json()[0]
        for field in ['id', 'sender_email', 'is_staff', 'text', 'is_read', 'created_at']:
            self.assertIn(field, msg)

    def test_is_staff_flag_correct(self):
        r = self.client.get('/api/chat/messages/', **self.user_auth())
        msgs = {m['id']: m for m in r.json()}
        self.assertTrue(msgs[self.msg1.id]['is_staff'])
        self.assertFalse(msgs[self.msg2.id]['is_staff'])


# ── chat_send ─────────────────────────────────────────────────────────────────

class ChatSendTest(ChatTestBase):
    def test_requires_auth(self):
        r = self.client.post('/api/chat/messages/send/', {'text': 'Привет'}, content_type='application/json')
        self.assertEqual(r.status_code, 401)

    def test_sends_message(self):
        self.client.post('/api/chat/messages/send/', {'text': 'Привет'}, content_type='application/json', **self.user_auth())
        self.assertEqual(ChatMessage.objects.filter(chat__user=self.user).count(), 1)

    def test_returns_201(self):
        r = self.client.post('/api/chat/messages/send/', {'text': 'Привет'}, content_type='application/json', **self.user_auth())
        self.assertEqual(r.status_code, 201)

    def test_empty_text_returns_400(self):
        r = self.client.post('/api/chat/messages/send/', {'text': ''}, content_type='application/json', **self.user_auth())
        self.assertEqual(r.status_code, 400)

    def test_updates_last_message_at(self):
        self.client.post('/api/chat/messages/send/', {'text': 'Привет'}, content_type='application/json', **self.user_auth())
        chat = Chat.objects.get(user=self.user)
        self.assertIsNotNone(chat.last_message_at)

    def test_sender_is_current_user(self):
        self.client.post('/api/chat/messages/send/', {'text': 'Привет'}, content_type='application/json', **self.user_auth())
        msg = ChatMessage.objects.get(chat__user=self.user)
        self.assertEqual(msg.sender, self.user)


# ── chat_mark_read ────────────────────────────────────────────────────────────

class ChatMarkReadTest(ChatTestBase):
    def setUp(self):
        super().setUp()
        self.chat = self.make_chat()
        self.staff_msg = self.make_message(self.chat, self.staff, 'От staff')
        self.user_msg = self.make_message(self.chat, self.user, 'От user')

    def test_requires_auth(self):
        r = self.client.post('/api/chat/mark-read/', {'up_to_id': self.staff_msg.id}, content_type='application/json')
        self.assertEqual(r.status_code, 401)

    def test_marks_staff_messages_as_read(self):
        self.client.post('/api/chat/mark-read/', {'up_to_id': self.staff_msg.id}, content_type='application/json', **self.user_auth())
        self.staff_msg.refresh_from_db()
        self.assertTrue(self.staff_msg.is_read)

    def test_does_not_mark_own_messages_as_read(self):
        self.client.post('/api/chat/mark-read/', {'up_to_id': self.user_msg.id}, content_type='application/json', **self.user_auth())
        self.user_msg.refresh_from_db()
        self.assertFalse(self.user_msg.is_read)

    def test_missing_up_to_id_returns_400(self):
        r = self.client.post('/api/chat/mark-read/', {}, content_type='application/json', **self.user_auth())
        self.assertEqual(r.status_code, 400)


# ── chat_unread ───────────────────────────────────────────────────────────────

class ChatUnreadTest(ChatTestBase):
    def test_requires_auth(self):
        r = self.client.get('/api/chat/unread/')
        self.assertEqual(r.status_code, 401)

    def test_zero_when_no_chat(self):
        r = self.client.get('/api/chat/unread/', **self.user_auth())
        self.assertEqual(r.json()['unread'], 0)

    def test_counts_unread_staff_messages(self):
        chat = self.make_chat()
        self.make_message(chat, self.staff)
        self.make_message(chat, self.staff)
        r = self.client.get('/api/chat/unread/', **self.user_auth())
        self.assertEqual(r.json()['unread'], 2)

    def test_excludes_own_messages(self):
        chat = self.make_chat()
        self.make_message(chat, self.user)
        r = self.client.get('/api/chat/unread/', **self.user_auth())
        self.assertEqual(r.json()['unread'], 0)

    def test_excludes_already_read_messages(self):
        chat = self.make_chat()
        self.make_message(chat, self.staff, is_read=True)
        r = self.client.get('/api/chat/unread/', **self.user_auth())
        self.assertEqual(r.json()['unread'], 0)


# ── staff_chat_list ───────────────────────────────────────────────────────────

class StaffChatListTest(ChatTestBase):
    def test_requires_staff(self):
        r = self.client.get('/api/chats/', **self.user_auth())
        self.assertEqual(r.status_code, 403)

    def test_requires_auth(self):
        r = self.client.get('/api/chats/')
        self.assertEqual(r.status_code, 401)

    def test_returns_all_chats(self):
        self.make_chat(self.user)
        r = self.client.get('/api/chats/', **self.staff_auth())
        self.assertEqual(len(r.json()), 1)

    def test_chat_has_expected_fields(self):
        self.make_chat(self.user)
        r = self.client.get('/api/chats/', **self.staff_auth())
        chat = r.json()[0]
        for field in ['id', 'user_email', 'unread_count', 'last_message_at']:
            self.assertIn(field, chat)

    def test_unread_count_correct(self):
        chat = self.make_chat(self.user)
        self.make_message(chat, self.user)
        self.make_message(chat, self.user)
        r = self.client.get('/api/chats/', **self.staff_auth())
        self.assertEqual(r.json()[0]['unread_count'], 2)

    def test_sorted_by_last_message_at(self):
        user2 = User.objects.create_user(email='user2@example.com', password='pass')
        chat1 = self.make_chat(self.user)
        chat2 = self.make_chat(user2)
        self.make_message(chat1, self.user, 'Старое')
        self.make_message(chat2, user2, 'Новое')
        r = self.client.get('/api/chats/', **self.staff_auth())
        self.assertEqual(r.json()[0]['user_email'], 'user2@example.com')


# ── staff_chat_messages ───────────────────────────────────────────────────────

class StaffChatMessagesTest(ChatTestBase):
    def setUp(self):
        super().setUp()
        self.chat = self.make_chat(self.user)
        self.msg = self.make_message(self.chat, self.user, 'Привет')

    def test_requires_staff(self):
        r = self.client.get(f'/api/chats/{self.user.id}/messages/', **self.user_auth())
        self.assertEqual(r.status_code, 403)

    def test_returns_messages(self):
        r = self.client.get(f'/api/chats/{self.user.id}/messages/', **self.staff_auth())
        self.assertEqual(len(r.json()), 1)
        self.assertEqual(r.json()[0]['text'], 'Привет')

    def test_404_when_no_chat(self):
        r = self.client.get(f'/api/chats/99999/messages/', **self.staff_auth())
        self.assertEqual(r.status_code, 404)


# ── staff_chat_send ───────────────────────────────────────────────────────────

class StaffChatSendTest(ChatTestBase):
    def setUp(self):
        super().setUp()
        self.chat = self.make_chat(self.user)

    def test_requires_staff(self):
        r = self.client.post(f'/api/chats/{self.user.id}/messages/send/', {'text': 'Ответ'}, content_type='application/json', **self.user_auth())
        self.assertEqual(r.status_code, 403)

    def test_sends_message(self):
        self.client.post(f'/api/chats/{self.user.id}/messages/send/', {'text': 'Ответ'}, content_type='application/json', **self.staff_auth())
        self.assertEqual(ChatMessage.objects.filter(chat=self.chat).count(), 1)

    def test_sender_is_staff(self):
        self.client.post(f'/api/chats/{self.user.id}/messages/send/', {'text': 'Ответ'}, content_type='application/json', **self.staff_auth())
        msg = ChatMessage.objects.get(chat=self.chat)
        self.assertEqual(msg.sender, self.staff)

    def test_404_when_no_chat(self):
        r = self.client.post(f'/api/chats/99999/messages/send/', {'text': 'Ответ'}, content_type='application/json', **self.staff_auth())
        self.assertEqual(r.status_code, 404)


# ── staff_chat_mark_read ──────────────────────────────────────────────────────

class StaffChatMarkReadTest(ChatTestBase):
    def setUp(self):
        super().setUp()
        self.chat = self.make_chat(self.user)
        self.msg = self.make_message(self.chat, self.user)

    def test_requires_staff(self):
        r = self.client.post(f'/api/chats/{self.user.id}/mark-read/', {'up_to_id': self.msg.id}, content_type='application/json', **self.user_auth())
        self.assertEqual(r.status_code, 403)

    def test_marks_user_messages_as_read(self):
        self.client.post(f'/api/chats/{self.user.id}/mark-read/', {'up_to_id': self.msg.id}, content_type='application/json', **self.staff_auth())
        self.msg.refresh_from_db()
        self.assertTrue(self.msg.is_read)

    def test_missing_up_to_id_returns_400(self):
        r = self.client.post(f'/api/chats/{self.user.id}/mark-read/', {}, content_type='application/json', **self.staff_auth())
        self.assertEqual(r.status_code, 400)


# ── staff_unread ──────────────────────────────────────────────────────────────

class StaffUnreadTest(ChatTestBase):
    def test_requires_staff(self):
        r = self.client.get('/api/chats/unread/', **self.user_auth())
        self.assertEqual(r.status_code, 403)

    def test_zero_when_no_messages(self):
        r = self.client.get('/api/chats/unread/', **self.staff_auth())
        self.assertEqual(r.json()['unread'], 0)

    def test_counts_unread_from_all_users(self):
        user2 = User.objects.create_user(email='user2@example.com', password='pass')
        chat1 = self.make_chat(self.user)
        chat2 = self.make_chat(user2)
        self.make_message(chat1, self.user)
        self.make_message(chat2, user2)
        r = self.client.get('/api/chats/unread/', **self.staff_auth())
        self.assertEqual(r.json()['unread'], 2)

    def test_excludes_read_messages(self):
        chat = self.make_chat(self.user)
        self.make_message(chat, self.user, is_read=True)
        r = self.client.get('/api/chats/unread/', **self.staff_auth())
        self.assertEqual(r.json()['unread'], 0)

    def test_excludes_staff_own_messages(self):
        chat = self.make_chat(self.user)
        self.make_message(chat, self.staff)
        r = self.client.get('/api/chats/unread/', **self.staff_auth())
        self.assertEqual(r.json()['unread'], 0)
