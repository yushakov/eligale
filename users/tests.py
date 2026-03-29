from datetime import timedelta
from unittest.mock import patch

from django.test import TestCase, Client
from django.utils import timezone
from rest_framework.authtoken.models import Token

from .models import EmailVerification
from django.contrib.auth import get_user_model

User = get_user_model()


class RequestCodeTest(TestCase):
    def setUp(self):
        self.client = Client()

    @patch('users.views.send_mail')
    def test_creates_email_verification(self, mock_mail):
        self.client.post('/api/auth/request-code/', {'email': 'user@example.com'},
                         content_type='application/json')
        self.assertEqual(EmailVerification.objects.filter(email='user@example.com').count(), 1)

    @patch('users.views.send_mail')
    def test_sends_email(self, mock_mail):
        self.client.post('/api/auth/request-code/', {'email': 'user@example.com'},
                         content_type='application/json')
        mock_mail.assert_called_once()
        args, kwargs = mock_mail.call_args
        self.assertIn('user@example.com', kwargs.get('recipient_list', args[3] if len(args) > 3 else []))

    @patch('users.views.send_mail')
    def test_returns_ok(self, mock_mail):
        r = self.client.post('/api/auth/request-code/', {'email': 'user@example.com'},
                             content_type='application/json')
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.json()['ok'])

    def test_missing_email_returns_400(self):
        r = self.client.post('/api/auth/request-code/', {}, content_type='application/json')
        self.assertEqual(r.status_code, 400)

    @patch('users.views.send_mail')
    def test_email_normalized_to_lowercase(self, mock_mail):
        self.client.post('/api/auth/request-code/', {'email': 'User@Example.COM'},
                         content_type='application/json')
        self.assertTrue(EmailVerification.objects.filter(email='user@example.com').exists())


class VerifyCodeTest(TestCase):
    def setUp(self):
        self.client = Client()

    def _create_verification(self, email='user@example.com', code='123456', is_used=False,
                              age_minutes=0):
        v = EmailVerification.objects.create(email=email, code=code, is_used=is_used)
        if age_minutes:
            EmailVerification.objects.filter(pk=v.pk).update(
                created_at=timezone.now() - timedelta(minutes=age_minutes)
            )
        return EmailVerification.objects.get(pk=v.pk)

    def test_valid_code_returns_token(self):
        self._create_verification()
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '123456'},
                             content_type='application/json')
        self.assertEqual(r.status_code, 200)
        self.assertIn('token', r.json())

    def test_valid_code_marks_verification_used(self):
        v = self._create_verification()
        self.client.post('/api/auth/verify-code/',
                         {'email': 'user@example.com', 'code': '123456'},
                         content_type='application/json')
        v.refresh_from_db()
        self.assertTrue(v.is_used)

    def test_valid_code_creates_user(self):
        self._create_verification()
        self.client.post('/api/auth/verify-code/',
                         {'email': 'user@example.com', 'code': '123456'},
                         content_type='application/json')
        self.assertTrue(User.objects.filter(email='user@example.com').exists())

    def test_has_name_false_for_new_user(self):
        self._create_verification()
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '123456'},
                             content_type='application/json')
        self.assertFalse(r.json()['has_name'])

    def test_has_name_true_when_display_name_set(self):
        user = User.objects.create_user(email='user@example.com')
        user.display_name = 'Аня'
        user.save()
        Token.objects.create(user=user)
        self._create_verification()
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '123456'},
                             content_type='application/json')
        self.assertTrue(r.json()['has_name'])

    def test_wrong_code_returns_400(self):
        self._create_verification()
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '000000'},
                             content_type='application/json')
        self.assertEqual(r.status_code, 400)

    def test_used_code_returns_400(self):
        self._create_verification(is_used=True)
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '123456'},
                             content_type='application/json')
        self.assertEqual(r.status_code, 400)

    def test_expired_code_returns_400(self):
        self._create_verification(age_minutes=20)
        r = self.client.post('/api/auth/verify-code/',
                             {'email': 'user@example.com', 'code': '123456'},
                             content_type='application/json')
        self.assertEqual(r.status_code, 400)

    def test_missing_fields_returns_400(self):
        r = self.client.post('/api/auth/verify-code/', {}, content_type='application/json')
        self.assertEqual(r.status_code, 400)


class SetNameTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com')
        self.token = Token.objects.create(user=self.user)

    def _post(self, name, token=None):
        headers = {}
        if token:
            headers['HTTP_AUTHORIZATION'] = f'Token {token}'
        return self.client.post(
            '/api/auth/set-name/',
            {'name': name},
            content_type='application/json',
            **headers,
        )

    def test_sets_display_name(self):
        self._post('Мария', token=self.token.key)
        self.user.refresh_from_db()
        self.assertEqual(self.user.display_name, 'Мария')

    def test_returns_display_name(self):
        r = self._post('Мария', token=self.token.key)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['display_name'], 'Мария')

    def test_requires_auth(self):
        r = self._post('Мария')
        self.assertEqual(r.status_code, 401)

    def test_empty_name_returns_400(self):
        r = self._post('   ', token=self.token.key)
        self.assertEqual(r.status_code, 400)


class UniqueDisplayNameTest(TestCase):
    """Тест логики уникальности display_name."""

    def setUp(self):
        self.client = Client()

    def _set_name(self, email, name):
        user = User.objects.create_user(email=email)
        token = Token.objects.create(user=user)
        r = self.client.post(
            '/api/auth/set-name/',
            {'name': name},
            content_type='application/json',
            HTTP_AUTHORIZATION=f'Token {token.key}',
        )
        user.refresh_from_db()
        return user.display_name

    def test_unique_name_used_as_is(self):
        name = self._set_name('a@example.com', 'Аня')
        self.assertEqual(name, 'Аня')

    def test_duplicate_name_gets_hash_suffix(self):
        self._set_name('a@example.com', 'Аня')
        name2 = self._set_name('b@example.com', 'Аня')
        self.assertTrue(name2.startswith('Аня #'))
        self.assertEqual(len(name2), len('Аня #') + 4)

    def test_all_display_names_are_unique(self):
        names = [self._set_name(f'user{i}@example.com', 'Аня') for i in range(5)]
        self.assertEqual(len(set(names)), 5)

    def test_same_name_returned_without_hash(self):
        """Если пользователь отправляет своё же имя — хэш не добавляется."""
        name1 = self._set_name('a@example.com', 'Аня')
        self.assertEqual(name1, 'Аня')
        user = User.objects.get(email='a@example.com')
        token = Token.objects.get(user=user)
        r = self.client.post(
            '/api/auth/set-name/',
            {'name': 'Аня'},
            content_type='application/json',
            HTTP_AUTHORIZATION=f'Token {token.key}',
        )
        self.assertEqual(r.json()['display_name'], 'Аня')


class ProfileTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com')
        self.user.display_name = 'Мария'
        self.user.save()
        self.token = Token.objects.create(user=self.user)

    def _get(self, token=None):
        headers = {}
        if token:
            headers['HTTP_AUTHORIZATION'] = f'Token {token}'
        return self.client.get('/api/auth/profile/', **headers)

    def test_returns_email_and_display_name(self):
        r = self._get(token=self.token.key)
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.json()['email'], 'user@example.com')
        self.assertEqual(r.json()['display_name'], 'Мария')

    def test_empty_display_name_returned_as_empty_string(self):
        self.user.display_name = ''
        self.user.save()
        r = self._get(token=self.token.key)
        self.assertEqual(r.json()['display_name'], '')

    def test_requires_auth(self):
        r = self._get()
        self.assertEqual(r.status_code, 401)

    def test_wrong_token_returns_401(self):
        r = self._get(token='invalidtoken')
        self.assertEqual(r.status_code, 401)


class DeleteAccountTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com')
        self.token = Token.objects.create(user=self.user)

    def _delete(self, token=None):
        headers = {}
        if token:
            headers['HTTP_AUTHORIZATION'] = f'Token {token}'
        return self.client.delete('/api/auth/delete-account/', **headers)

    def test_deletes_user(self):
        self._delete(token=self.token.key)
        self.assertFalse(User.objects.filter(email='user@example.com').exists())

    def test_returns_ok(self):
        r = self._delete(token=self.token.key)
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.json()['ok'])

    def test_requires_auth(self):
        r = self._delete()
        self.assertEqual(r.status_code, 401)

    def test_token_invalidated_after_deletion(self):
        """После удаления аккаунта токен тоже удаляется (каскад)."""
        token_key = self.token.key
        self._delete(token=token_key)
        self.assertFalse(Token.objects.filter(key=token_key).exists())

    def test_staff_cannot_delete_account(self):
        self.user.is_staff = True
        self.user.save()
        r = self._delete(token=self.token.key)
        self.assertEqual(r.status_code, 403)
        self.assertTrue(User.objects.filter(email='user@example.com').exists())


class LogoutTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com')
        self.token = Token.objects.create(user=self.user)

    def _post(self, token=None):
        headers = {}
        if token:
            headers['HTTP_AUTHORIZATION'] = f'Token {token}'
        return self.client.post('/api/auth/logout/', **headers)

    def test_requires_auth(self):
        r = self._post()
        self.assertEqual(r.status_code, 401)

    def test_returns_ok(self):
        r = self._post(token=self.token.key)
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.json()['ok'])

    def test_deletes_token_from_db(self):
        key = self.token.key
        self._post(token=key)
        self.assertFalse(Token.objects.filter(key=key).exists())

    def test_subsequent_request_returns_401(self):
        key = self.token.key
        self._post(token=key)
        r = self.client.get('/api/auth/profile/', HTTP_AUTHORIZATION=f'Token {key}')
        self.assertEqual(r.status_code, 401)


class ProfileIsStaffTest(TestCase):
    def setUp(self):
        self.client = Client()
        self.user = User.objects.create_user(email='user@example.com')
        self.token = Token.objects.create(user=self.user)

    def _get(self):
        return self.client.get('/api/auth/profile/', HTTP_AUTHORIZATION=f'Token {self.token.key}')

    def test_is_staff_false_for_regular_user(self):
        r = self._get()
        self.assertFalse(r.json()['is_staff'])

    def test_is_staff_true_for_staff_user(self):
        self.user.is_staff = True
        self.user.save()
        r = self._get()
        self.assertTrue(r.json()['is_staff'])
