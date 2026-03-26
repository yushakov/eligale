import io
from unittest.mock import MagicMock, patch

from django.test import TestCase, Client
from django.contrib.auth import get_user_model

User = get_user_model()


def _make_mock_s3(presign_return='https://storage.yandexcloud.net/presigned-url'):
    mock_client = MagicMock()
    mock_client.generate_presigned_url.return_value = presign_return
    mock_client.put_object.return_value = {'ETag': '"abc123"'}
    mock_session = MagicMock()
    mock_session.client.return_value = mock_client
    return mock_session, mock_client


class PublicPresignAPITest(TestCase):
    """Тест presign для публичного хранилища (/api/test-image-upload/presign)."""

    def setUp(self):
        self.client = Client()

    def _post(self, data):
        return self.client.post('/api/test-image-upload/presign', data)

    @patch('uploader.views.boto3.session.Session')
    def test_returns_expected_fields(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertTrue(data['ok'])
        self.assertIn('upload_url', data)
        self.assertIn('public_url', data)
        self.assertIn('object_key', data)
        self.assertIn('content_type', data)

    @patch('uploader.views.boto3.session.Session')
    def test_object_key_starts_with_test_uploads(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        self.assertTrue(r.json()['object_key'].startswith('test-uploads/'))

    @patch('uploader.views.boto3.session.Session')
    def test_object_key_format(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'My Ring.JPG', 'content_type': 'image/jpeg'})
        import re
        self.assertRegex(
            r.json()['object_key'],
            r'^test-uploads/\d{4}-\d{2}-\d{2}/[\w-]+-[0-9a-f]{32}\.jpg$'
        )

    @patch('uploader.views.boto3.session.Session')
    def test_public_url_contains_object_key(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        data = r.json()
        self.assertIn(data['object_key'], data['public_url'])

    @patch('uploader.views.boto3.session.Session')
    def test_content_type_guessed_when_missing(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'image.png'})
        self.assertEqual(r.json()['content_type'], 'image/png')

    @patch('uploader.views.boto3.session.Session')
    def test_content_type_fallback(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'data.bin'})
        self.assertEqual(r.json()['content_type'], 'application/octet-stream')

    def test_missing_filename_returns_400(self):
        r = self._post({'content_type': 'image/jpeg'})
        self.assertEqual(r.status_code, 400)

    @patch('uploader.views.boto3.session.Session')
    def test_presign_uses_public_bucket(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_session_cls.return_value = mock_session
        from django.conf import settings
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        call_kwargs = mock_s3.generate_presigned_url.call_args
        params = call_kwargs[1]['Params']
        self.assertEqual(params['Bucket'], settings.YA_PUBLIC_UPLOADER_BUCKET_NAME)

    @patch('uploader.views.boto3.session.Session')
    def test_presign_http_method_is_put(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_session_cls.return_value = mock_session
        self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        call_kwargs = mock_s3.generate_presigned_url.call_args
        self.assertEqual(call_kwargs[1].get('HttpMethod'), 'PUT')


class PublicDirectUploadAPITest(TestCase):
    """Тест прямой загрузки в публичный бакет (/api/test-image-upload)."""

    def setUp(self):
        self.client = Client()
        self.staff = User.objects.create_superuser(email='admin@example.com', password='pass')

    @patch('uploader.views.boto3.session.Session')
    def test_upload_returns_ok(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        self.client.force_login(self.staff)
        fake_file = io.BytesIO(b'fake image data')
        fake_file.name = 'test.jpg'
        r = self.client.post(
            '/api/test-image-upload',
            {'file': fake_file},
            format='multipart',
        )
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertTrue(data['ok'])
        self.assertIn('url', data)
        self.assertIn('object_key', data)
        self.assertIn('etag', data)

    @patch('uploader.views.boto3.session.Session')
    def test_upload_calls_put_object(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_session_cls.return_value = mock_session
        self.client.force_login(self.staff)
        fake_file = io.BytesIO(b'hello')
        fake_file.name = 'hello.jpg'
        self.client.post('/api/test-image-upload', {'file': fake_file})
        mock_s3.put_object.assert_called_once()

    def test_missing_file_returns_400(self):
        r = self.client.post('/api/test-image-upload', {})
        self.assertEqual(r.status_code, 400)


class PrivatePresignAPITest(TestCase):
    """Тест presign для приватного (зашифрованного) хранилища (/api/test-private-upload/presign)."""

    def setUp(self):
        self.client = Client()
        self.staff = User.objects.create_superuser(email='admin@example.com', password='pass')
        self.client.force_login(self.staff)

    def _post(self, data):
        return self.client.post('/api/test-private-upload/presign', data)

    @patch('uploader.views.boto3.session.Session')
    def test_returns_both_upload_and_view_urls(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        # первый вызов — upload_url, второй — view_url
        mock_s3.generate_presigned_url.side_effect = [
            'https://storage.yandexcloud.net/upload-url',
            'https://storage.yandexcloud.net/view-url',
        ]
        mock_session_cls.return_value = mock_session
        r = self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertTrue(data['ok'])
        self.assertIn('upload_url', data)
        self.assertIn('view_url', data)
        self.assertEqual(data['upload_url'], 'https://storage.yandexcloud.net/upload-url')
        self.assertEqual(data['view_url'], 'https://storage.yandexcloud.net/view-url')

    @patch('uploader.views.boto3.session.Session')
    def test_upload_presign_uses_put(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        upload_call = mock_s3.generate_presigned_url.call_args_list[0]
        self.assertEqual(upload_call[1].get('HttpMethod'), 'PUT')

    @patch('uploader.views.boto3.session.Session')
    def test_view_presign_uses_get(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        view_call = mock_s3.generate_presigned_url.call_args_list[1]
        # вызов через keyword: generate_presigned_url(ClientMethod='get_object', ...)
        self.assertEqual(view_call[1].get('ClientMethod') or view_call[0][0], 'get_object')

    @patch('uploader.views.boto3.session.Session')
    def test_uses_private_bucket(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        from django.conf import settings
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        upload_call = mock_s3.generate_presigned_url.call_args_list[0]
        self.assertEqual(upload_call[1]['Params']['Bucket'], settings.YA_PRIVATE_UPLOADER_BUCKET_NAME)

    @patch('uploader.views.boto3.session.Session')
    def test_view_url_expires_in_60s(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        view_call = mock_s3.generate_presigned_url.call_args_list[1]
        self.assertEqual(view_call[1].get('ExpiresIn'), 60)

    @patch('uploader.views.boto3.session.Session')
    def test_upload_url_expires_in_3600s(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        upload_call = mock_s3.generate_presigned_url.call_args_list[0]
        self.assertEqual(upload_call[1].get('ExpiresIn'), 3600)

    def test_missing_filename_returns_400(self):
        r = self._post({})
        self.assertEqual(r.status_code, 400)

    @patch('uploader.views.boto3.session.Session')
    def test_content_type_guessed(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        r = self._post({'filename': 'image.webp'})
        self.assertEqual(r.json()['content_type'], 'image/webp')

    @patch('uploader.views.boto3.session.Session')
    def test_private_client_uses_private_credentials(self, mock_session_cls):
        """Клиент для приватного бакета создаётся с приватными ключами."""
        mock_session, mock_s3 = _make_mock_s3()
        mock_s3.generate_presigned_url.side_effect = ['upload-url', 'view-url']
        mock_session_cls.return_value = mock_session
        from django.conf import settings
        self._post({'filename': 'private.jpg', 'content_type': 'image/jpeg'})
        call_kwargs = mock_session.client.call_args[1]
        self.assertEqual(call_kwargs['aws_access_key_id'], settings.YA_PRIVATE_UPLOADER_ACCESS_KEY_ID)
        self.assertEqual(call_kwargs['aws_secret_access_key'], settings.YA_PRIVATE_UPLOADER_SECRET_ACCESS_KEY)
