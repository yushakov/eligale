from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('users', '0002_add_display_name'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='privacy_and_terms_of_use_consent_date',
            field=models.DateTimeField(blank=True, null=True),
        ),
    ]
