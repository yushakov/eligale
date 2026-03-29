from django.db.models.signals import post_save
from django.dispatch import receiver

from .models import ProductImage
from .thumbnails import generate_thumbnails_async


@receiver(post_save, sender=ProductImage)
def on_product_image_saved(sender, instance, created, **kwargs):
    """Generate thumbnails in the background when a new ProductImage is created."""
    if created:
        generate_thumbnails_async(instance.image_key)
