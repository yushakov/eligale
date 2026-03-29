"""
Management command: generate thumbnails for images that don't have them yet.

Usage:
    python manage.py generate_missing_thumbnails
    python manage.py generate_missing_thumbnails --force   # regenerate even existing ones

Run this once after deploying thumbnail support to backfill existing images,
and as a periodic safety net (e.g. daily cron) to fix any gaps.
"""

from django.core.management.base import BaseCommand

from catalog.models import Category, Product, ProductImage
from catalog.thumbnails import generate_thumbnails, thumbnails_exist


class Command(BaseCommand):
    help = 'Generate missing thumbnails for all ProductImage, Category, and Product cover keys'

    def add_arguments(self, parser):
        parser.add_argument(
            '--force',
            action='store_true',
            help='Regenerate thumbnails even if they already exist',
        )

    def handle(self, *args, **options):
        force = options['force']

        keys = []

        # ProductImage gallery photos
        for img in ProductImage.objects.all():
            if img.image_key:
                keys.append(('ProductImage', img.id, img.image_key))

        # Category covers
        for cat in Category.objects.exclude(cover_key=''):
            keys.append(('Category', cat.id, cat.cover_key))

        # Product covers
        for prod in Product.objects.exclude(cover_key=''):
            keys.append(('Product', prod.id, prod.cover_key))

        total = len(keys)
        self.stdout.write(f'Found {total} images to check.')

        done = 0
        skipped = 0
        errors = 0

        for model_name, obj_id, key in keys:
            label = f'{model_name} #{obj_id} ({key})'
            if not force and thumbnails_exist(key):
                skipped += 1
                continue
            self.stdout.write(f'  Generating thumbnails for {label}...')
            try:
                generate_thumbnails(key)
                done += 1
            except Exception as e:
                self.stderr.write(f'  ERROR for {label}: {e}')
                errors += 1

        self.stdout.write(self.style.SUCCESS(
            f'Done. Generated: {done}, skipped (exist): {skipped}, errors: {errors}.'
        ))
