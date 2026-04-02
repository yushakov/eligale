from django.db import migrations, models


def initialize_order(apps, schema_editor):
    Category = apps.get_model('catalog', 'Category')
    for i, cat in enumerate(Category.objects.order_by('name')):
        cat.order = i
        cat.save(update_fields=['order'])


class Migration(migrations.Migration):

    dependencies = [
        ('catalog', '0010_add_apprelease'),
    ]

    operations = [
        migrations.AddField(
            model_name='category',
            name='order',
            field=models.IntegerField(default=0),
        ),
        migrations.RunPython(initialize_order, migrations.RunPython.noop),
    ]
