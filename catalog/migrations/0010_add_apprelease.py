from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('catalog', '0009_comment_report'),
    ]

    operations = [
        migrations.CreateModel(
            name='AppRelease',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('version_code', models.IntegerField(unique=True)),
                ('version_name', models.CharField(max_length=20)),
                ('apk_key', models.CharField(blank=True, default='', max_length=1024)),
                ('release_notes', models.TextField(blank=True, default='')),
                ('created_at', models.DateTimeField(auto_now_add=True)),
            ],
            options={
                'verbose_name': 'App Release',
                'verbose_name_plural': 'App Releases',
                'ordering': ['-version_code'],
            },
        ),
    ]
