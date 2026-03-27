import json
import re
from unittest.mock import MagicMock, patch

from django.test import TestCase, Client
from django.contrib.auth import get_user_model
from rest_framework.authtoken.models import Token
from .models import Category, Product, ProductImage, Comment

User = get_user_model()


class CategoryListAPITest(TestCase):
    def setUp(self):
        self.client = Client()
        self.visible = Category.objects.create(name='Видимая')
        self.hidden = Category.objects.create(name='Скрытая', is_hidden=True)

    def test_returns_visible_categories(self):
        r = self.client.get('/api/categories/')
        self.assertEqual(r.status_code, 200)
        names = [c['name'] for c in r.json()]
        self.assertIn('Видимая', names)

    def test_excludes_hidden_categories(self):
        r = self.client.get('/api/categories/')
        names = [c['name'] for c in r.json()]
        self.assertNotIn('Скрытая', names)

    def test_cover_url_built_correctly(self):
        self.visible.cover_key = 'catalog/2024-01-01/photo-abc.jpg'
        self.visible.save()
        r = self.client.get('/api/categories/')
        cat = next(c for c in r.json() if c['name'] == 'Видимая')
        self.assertIn('catalog/2024-01-01/photo-abc.jpg', cat['cover_url'])

    def test_cover_url_none_when_no_key(self):
        r = self.client.get('/api/categories/')
        cat = next(c for c in r.json() if c['name'] == 'Видимая')
        self.assertIsNone(cat['cover_url'])


class ProductListAPITest(TestCase):
    def setUp(self):
        self.client = Client()
        self.category = Category.objects.create(name='Украшения')
        self.visible = Product.objects.create(name='Кольцо')
        self.visible.categories.add(self.category)
        self.hidden = Product.objects.create(name='Скрытое кольцо', is_hidden=True)
        self.hidden.categories.add(self.category)

    def test_returns_visible_products(self):
        r = self.client.get(f'/api/categories/{self.category.pk}/products/')
        self.assertEqual(r.status_code, 200)
        names = [p['name'] for p in r.json()]
        self.assertIn('Кольцо', names)

    def test_excludes_hidden_products(self):
        r = self.client.get(f'/api/categories/{self.category.pk}/products/')
        names = [p['name'] for p in r.json()]
        self.assertNotIn('Скрытое кольцо', names)

    def test_404_for_missing_category(self):
        r = self.client.get('/api/categories/99999/products/')
        self.assertEqual(r.status_code, 404)


class ProductDetailAPITest(TestCase):
    def setUp(self):
        self.client = Client()
        self.product = Product.objects.create(
            name='Серьги', description='Золотые', cover_key='catalog/2024-01-01/earrings.jpg'
        )
        ProductImage.objects.create(product=self.product, image_key='catalog/2024-01-01/img1.jpg')
        ProductImage.objects.create(product=self.product, image_key='catalog/2024-01-01/img2.jpg')

    def test_returns_product_detail(self):
        r = self.client.get(f'/api/products/{self.product.pk}/')
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertEqual(data['name'], 'Серьги')
        self.assertEqual(data['description'], 'Золотые')

    def test_cover_url_in_response(self):
        r = self.client.get(f'/api/products/{self.product.pk}/')
        self.assertIn('catalog/2024-01-01/earrings.jpg', r.json()['cover_url'])

    def test_images_in_response(self):
        r = self.client.get(f'/api/products/{self.product.pk}/')
        images = r.json()['images']
        self.assertEqual(len(images), 2)
        urls = [img['image_url'] for img in images]
        self.assertTrue(all('catalog/2024-01-01/img' in u for u in urls))

    def test_404_for_missing_product(self):
        r = self.client.get('/api/products/99999/')
        self.assertEqual(r.status_code, 404)


class CommentListAPITest(TestCase):
    def setUp(self):
        self.client = Client()
        self.product = Product.objects.create(name='Браслет')
        self.user = User.objects.create_user(email='test@example.com')
        self.token = Token.objects.create(user=self.user)
        Comment.objects.create(product=self.product, user=self.user, text='Красиво!')

    def test_get_comments_public(self):
        r = self.client.get(f'/api/products/{self.product.pk}/comments/')
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]['text'], 'Красиво!')

    def test_comment_author_uses_display_name(self):
        self.user.display_name = 'Аня'
        self.user.save()
        r = self.client.get(f'/api/products/{self.product.pk}/comments/')
        self.assertEqual(r.json()[0]['author'], 'Аня')

    def test_comment_author_falls_back_to_email_prefix(self):
        r = self.client.get(f'/api/products/{self.product.pk}/comments/')
        self.assertEqual(r.json()[0]['author'], 'test')

    def test_post_comment_requires_auth(self):
        r = self.client.post(
            f'/api/products/{self.product.pk}/comments/',
            data=json.dumps({'text': 'Привет'}),
            content_type='application/json',
        )
        self.assertEqual(r.status_code, 401)

    def test_post_comment_with_valid_token(self):
        r = self.client.post(
            f'/api/products/{self.product.pk}/comments/',
            data=json.dumps({'text': 'Новый комментарий'}),
            content_type='application/json',
            HTTP_AUTHORIZATION=f'Token {self.token.key}',
        )
        self.assertEqual(r.status_code, 201)
        self.assertEqual(Comment.objects.filter(product=self.product).count(), 2)

    def test_post_comment_empty_text_rejected(self):
        r = self.client.post(
            f'/api/products/{self.product.pk}/comments/',
            data=json.dumps({'text': '   '}),
            content_type='application/json',
            HTTP_AUTHORIZATION=f'Token {self.token.key}',
        )
        self.assertEqual(r.status_code, 400)

    def test_post_comment_404_for_missing_product(self):
        r = self.client.post(
            '/api/products/99999/comments/',
            data=json.dumps({'text': 'Текст'}),
            content_type='application/json',
            HTTP_AUTHORIZATION=f'Token {self.token.key}',
        )
        self.assertEqual(r.status_code, 404)


class Mobile2CategoryDetailTest(TestCase):
    """Тест страницы категории в mobile2 — плиточный вид."""

    def setUp(self):
        self.client = Client()
        self.staff = User.objects.create_superuser(email='admin@example.com', password='pass')
        self.category = Category.objects.create(name='Посуда')
        self.visible = Product.objects.create(name='Чашка', cover_key='img/cup.jpg')
        self.visible.categories.add(self.category)
        self.hidden = Product.objects.create(name='Скрытая тарелка', cover_key='img/plate.jpg', is_hidden=True)
        self.hidden.categories.add(self.category)

    def _get(self):
        self.client.force_login(self.staff)
        return self.client.get(f'/mobile2/categories/{self.category.pk}/')

    def test_requires_staff(self):
        r = self.client.get(f'/mobile2/categories/{self.category.pk}/')
        self.assertEqual(r.status_code, 302)

    def test_returns_200_for_staff(self):
        self.assertEqual(self._get().status_code, 200)

    def test_visible_product_appears(self):
        r = self._get()
        self.assertContains(r, 'img/cup.jpg')

    def test_hidden_product_also_appears(self):
        # В отличие от API, скрытые товары видны в интерфейсе управления
        r = self._get()
        self.assertContains(r, 'img/plate.jpg')

    def test_hidden_product_has_card_hidden_class(self):
        r = self._get()
        content = r.content.decode()
        hidden_tile_start = content.find(f'id="prod-{self.hidden.pk}"')
        self.assertIn('card-hidden', content[hidden_tile_start - 50:hidden_tile_start + 20])

    def test_visible_product_has_no_card_hidden_class(self):
        r = self._get()
        content = r.content.decode()
        tile_start = content.find(f'id="prod-{self.visible.pk}"')
        self.assertNotIn('card-hidden', content[tile_start - 50:tile_start + 20])

    def test_tiles_link_to_product_detail(self):
        r = self._get()
        self.assertContains(r, f'/mobile2/products/{self.visible.pk}/')

    def test_toggle_hidden_button_present(self):
        r = self._get()
        self.assertContains(r, f'/mobile/products/{self.visible.pk}/toggle-hidden/')

    def test_add_product_link_present(self):
        r = self._get()
        self.assertContains(r, f'/mobile2/categories/{self.category.pk}/products/add/')


class Mobile2ProductAddTest(TestCase):
    """Тест логики разбивки товаров в mobile2."""

    def setUp(self):
        self.client = Client()
        self.staff = User.objects.create_superuser(email='admin@example.com', password='pass')
        self.client.force_login(self.staff)
        self.category = Category.objects.create(name='Тест')

    def _post(self, name, image_keys, split=False):
        return self.client.post(
            f'/mobile2/categories/{self.category.pk}/products/add/',
            {
                'name': name,
                'description': '',
                'image_keys': json.dumps(image_keys),
                'split': 'on' if split else '',
            },
        )

    def test_single_image_creates_one_product(self):
        self._post('Кольцо', ['key/img1.jpg'])
        self.assertEqual(Product.objects.count(), 1)
        p = Product.objects.first()
        self.assertEqual(p.cover_key, 'key/img1.jpg')

    def test_multiple_images_no_split_creates_one_product_with_gallery(self):
        self._post('Кольцо', ['k1.jpg', 'k2.jpg', 'k3.jpg'], split=False)
        self.assertEqual(Product.objects.count(), 1)
        p = Product.objects.first()
        self.assertEqual(p.cover_key, 'k1.jpg')
        # Все три фото в галерее, включая обложку
        self.assertEqual(ProductImage.objects.filter(product=p).count(), 3)

    def test_single_image_is_in_gallery(self):
        self._post('Кольцо', ['only.jpg'])
        p = Product.objects.first()
        self.assertEqual(p.cover_key, 'only.jpg')
        images = list(ProductImage.objects.filter(product=p).values_list('image_key', flat=True))
        self.assertEqual(images, ['only.jpg'])

    def test_cover_included_in_gallery_for_multiple_images(self):
        self._post('Серьги', ['cover.jpg', 'extra1.jpg', 'extra2.jpg'])
        p = Product.objects.first()
        keys = list(ProductImage.objects.filter(product=p).values_list('image_key', flat=True))
        self.assertIn('cover.jpg', keys)
        self.assertIn('extra1.jpg', keys)
        self.assertIn('extra2.jpg', keys)

    def test_split_each_product_has_one_gallery_image(self):
        self._post('Ваза', ['a.jpg', 'b.jpg', 'c.jpg'], split=True)
        for product in Product.objects.all():
            images = ProductImage.objects.filter(product=product)
            self.assertEqual(images.count(), 1)
            self.assertEqual(images.first().image_key, product.cover_key)

    def test_split_creates_one_product_per_image(self):
        self._post('Кольцо', ['k1.jpg', 'k2.jpg', 'k3.jpg'], split=True)
        self.assertEqual(Product.objects.count(), 3)
        names = list(Product.objects.order_by('pk').values_list('name', flat=True))
        self.assertEqual(names, ['Кольцо 1', 'Кольцо 2', 'Кольцо 3'])

    def test_split_each_product_has_correct_cover(self):
        self._post('Серьги', ['a.jpg', 'b.jpg'], split=True)
        keys = list(Product.objects.order_by('pk').values_list('cover_key', flat=True))
        self.assertEqual(keys, ['a.jpg', 'b.jpg'])

    def test_split_with_single_image_creates_one_product(self):
        self._post('Кольцо', ['only.jpg'], split=True)
        self.assertEqual(Product.objects.count(), 1)

    def test_no_images_creates_product_without_cover(self):
        self._post('Кольцо', [])
        self.assertEqual(Product.objects.count(), 1)
        p = Product.objects.first()
        self.assertEqual(p.cover_key, '')

    def test_all_products_assigned_to_category(self):
        self._post('Кольцо', ['k1.jpg', 'k2.jpg'], split=True)
        for p in Product.objects.all():
            self.assertIn(self.category, p.categories.all())


def _make_mock_s3(presign_return='https://storage.yandexcloud.net/presigned'):
    mock_client = MagicMock()
    mock_client.generate_presigned_url.return_value = presign_return
    mock_session = MagicMock()
    mock_session.client.return_value = mock_client
    return mock_session, mock_client


class PresignUploadAPITest(TestCase):
    """Тест presign-эндпоинта публичного хранилища (/api/catalog/upload/presign)."""

    def setUp(self):
        self.client = Client()
        self.staff = User.objects.create_superuser(email='admin@example.com', password='pass')
        self.client.force_login(self.staff)

    def _post(self, data):
        return self.client.post('/api/catalog/upload/presign', data)

    @patch('catalog.views.boto3.session.Session')
    def test_returns_expected_fields(self, mock_session_cls):
        mock_session_cls.return_value, _ = _make_mock_s3()[0], _make_mock_s3()[1]
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        self.assertEqual(r.status_code, 200)
        data = r.json()
        self.assertTrue(data['ok'])
        self.assertIn('upload_url', data)
        self.assertIn('object_key', data)
        self.assertIn('public_url', data)
        self.assertIn('content_type', data)

    @patch('catalog.views.boto3.session.Session')
    def test_object_key_format(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'My Photo.JPG', 'content_type': 'image/jpeg'})
        key = r.json()['object_key']
        # catalog/YYYY-MM-DD/slug-hexhex.jpg
        self.assertRegex(key, r'^catalog/\d{4}-\d{2}-\d{2}/[\w-]+-[0-9a-f]{32}\.jpg$')

    @patch('catalog.views.boto3.session.Session')
    def test_public_url_contains_object_key(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        data = r.json()
        self.assertIn(data['object_key'], data['public_url'])

    @patch('catalog.views.boto3.session.Session')
    def test_content_type_guessed_from_filename(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'photo.png'})  # без content_type
        self.assertEqual(r.json()['content_type'], 'image/png')

    @patch('catalog.views.boto3.session.Session')
    def test_content_type_fallback_for_unknown_extension(self, mock_session_cls):
        mock_session_cls.return_value = _make_mock_s3()[0]
        r = self._post({'filename': 'file.zzzzunknown'})
        self.assertEqual(r.json()['content_type'], 'application/octet-stream')

    def test_missing_filename_returns_400(self):
        r = self._post({'content_type': 'image/jpeg'})
        self.assertEqual(r.status_code, 400)

    def test_requires_staff(self):
        self.client.logout()
        r = self._post({'filename': 'photo.jpg', 'content_type': 'image/jpeg'})
        # редирект на логин
        self.assertIn(r.status_code, [302, 403])

    @patch('catalog.views.boto3.session.Session')
    def test_presign_called_with_correct_params(self, mock_session_cls):
        mock_session, mock_s3 = _make_mock_s3()
        mock_session_cls.return_value = mock_session
        r = self._post({'filename': 'ring.jpg', 'content_type': 'image/jpeg'})
        object_key = r.json()['object_key']
        mock_s3.generate_presigned_url.assert_called_once()
        call_kwargs = mock_s3.generate_presigned_url.call_args
        params = call_kwargs[1]['Params'] if call_kwargs[1] else call_kwargs[0][1]
        self.assertEqual(params['Key'], object_key)
        self.assertEqual(params['ContentType'], 'image/jpeg')
        self.assertEqual(call_kwargs[1].get('HttpMethod') or call_kwargs[0][2], 'PUT')
