from django.db.models.signals import pre_save, post_save
from django.dispatch import receiver

from .models import Category, ProductImage
from .thumbnails import generate_thumbnails_async


@receiver(post_save, sender=ProductImage)
def on_product_image_saved(sender, instance, created, **kwargs):
    """Generate thumbnails in the background when a new ProductImage is created."""
    if created:
        generate_thumbnails_async(instance.image_key)


@receiver(pre_save, sender=Category)
def on_category_pre_save(sender, instance, **kwargs):
    """Stash the old cover_key so post_save can detect changes."""
    if instance.pk:
        try:
            instance._old_cover_key = Category.objects.get(pk=instance.pk).cover_key
        except Category.DoesNotExist:
            instance._old_cover_key = None
    else:
        instance._old_cover_key = None


@receiver(post_save, sender=Category)
def on_category_saved(sender, instance, created, **kwargs):
    """Generate thumbnails when cover_key is set or changed."""
    if not instance.cover_key:
        return
    old_key = getattr(instance, '_old_cover_key', None)
    if created or instance.cover_key != old_key:
        generate_thumbnails_async(instance.cover_key)
