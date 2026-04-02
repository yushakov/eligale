from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('catalog', '0012_category_ordering'),
    ]

    operations = [
        migrations.AddField(
            model_name='productimage',
            name='is_hidden',
            field=models.BooleanField(default=False),
        ),
    ]
