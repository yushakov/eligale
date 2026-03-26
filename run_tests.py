#!/usr/bin/env python
"""
Запуск всех Django-тестов.

Использование:
    .venv/bin/python run_tests.py
"""
import sys
import os

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'eliza_backend.settings')

import django
django.setup()

from django.test.utils import get_runner
from django.conf import settings

runner_class = get_runner(settings)
runner = runner_class(verbosity=2, keepdb=False)
failures = runner.run_tests(['catalog.tests', 'users.tests', 'uploader.tests'])
sys.exit(bool(failures))
